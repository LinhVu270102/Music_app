const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFile } = require("child_process");

const axios = require("axios");
const { FieldPath } = require("firebase-admin/firestore");

const { getDb } = require("./firebaseAdmin");

const COLLECTION_SONGS = "songs";
const COLLECTION_AUDIO_FINGERPRINTS = "audioFingerprints";

const FINGERPRINT_STATUS = {
  PENDING: "pending",
  PROCESSING: "processing",
  UNIQUE: "unique",
  DUPLICATE: "duplicate",
  FAILED: "failed"
};

const FINGERPRINT_ALGORITHM = "chromaprint";
const FINGERPRINT_VERSION = 1;

function createHttpError(statusCode, message, detail = "") {
  const error = new Error(message);
  error.statusCode = statusCode;
  error.detail = detail;
  return error;
}

function getFpcalcPath() {
  return process.env.FPCALC_PATH || "fpcalc";
}

function now() {
  return Date.now();
}

function safeFileName(value) {
  return String(value || "audio")
    .replace(/[^a-zA-Z0-9_-]/g, "_")
    .slice(0, 80);
}

function buildFingerprintBuckets(fingerprint) {
  const text = String(fingerprint || "");
  const bucketSize = 32;
  const buckets = [];

  for (let index = 0; index < text.length; index += bucketSize) {
    const chunk = text.slice(index, index + bucketSize);
    if (!chunk) continue;

    buckets.push(
      crypto
        .createHash("sha1")
        .update(chunk)
        .digest("hex")
        .slice(0, 16)
    );
  }

  return [...new Set(buckets)].slice(0, 80);
}

function buildFingerprintHash(fingerprint) {
  return crypto
    .createHash("sha256")
    .update(String(fingerprint || ""))
    .digest("hex");
}

function getServerReachableBaseUrl() {
  return String(
    process.env.FINGERPRINT_LOCAL_PROXY_BASE_URL ||
      process.env.SOUNDCLOUD_API_BASE_URL ||
      "http://127.0.0.1:3000"
  ).replace(/\/+$/, "");
}

function normalizeServerReachableAudioUrl(songUrl) {
  const url = String(songUrl || "").trim();
  if (!url) return "";

  const localProxyBaseUrl = getServerReachableBaseUrl();

  return url
    .replace(/^http:\/\/10\.0\.2\.2:3000/i, localProxyBaseUrl)
    .replace(/^http:\/\/localhost:3000/i, localProxyBaseUrl)
    .replace(/^http:\/\/127\.0\.0\.1:3000/i, localProxyBaseUrl);
}

function soundCloudTrackIdFromSongId(songId) {
  const value = String(songId || "").trim();
  if (!value.toLowerCase().startsWith("soundcloud_")) return "";

  const trackId = value.replace(/^soundcloud_/i, "");
  return /^\d+$/.test(trackId) ? trackId : "";
}

async function resolveFreshSoundCloudProxyUrl(songId) {
  const trackId = soundCloudTrackIdFromSongId(songId);
  if (!trackId) return "";

  const requestUrl = `${getServerReachableBaseUrl()}/getStreamUrl?trackId=${encodeURIComponent(trackId)}`;
  const response = await axios.get(requestUrl, {
    timeout: 30000,
    validateStatus: (status) => status >= 200 && status < 300
  });

  return normalizeServerReachableAudioUrl(response.data?.streamUrl || "");
}

async function openAudioStream(songId, songUrl) {
  const finalSongUrl = normalizeServerReachableAudioUrl(songUrl);

  try {
    return await axios.get(finalSongUrl, {
      responseType: "stream",
      timeout: 60000,
      maxRedirects: 5,
      validateStatus: (status) => status >= 200 && status < 300
    });
  } catch (error) {
    const statusCode = error.response?.status || 0;
    const freshStreamUrl = statusCode === 403
      ? await resolveFreshSoundCloudProxyUrl(songId).catch(() => "")
      : "";

    if (!freshStreamUrl || freshStreamUrl === finalSongUrl) {
      throw error;
    }

    return await axios.get(freshStreamUrl, {
      responseType: "stream",
      timeout: 60000,
      maxRedirects: 5,
      validateStatus: (status) => status >= 200 && status < 300
    });
  }
}

async function downloadAudioToTempFile(songId, songUrl) {
  const filePath = path.join(os.tmpdir(), `orange-music-${safeFileName(songId)}-${Date.now()}.audio`);
  const writer = fs.createWriteStream(filePath);

  const response = await openAudioStream(songId, songUrl);

  await new Promise((resolve, reject) => {
    response.data.pipe(writer);
    response.data.on("error", reject);
    writer.on("finish", resolve);
    writer.on("error", reject);
  });

  return filePath;
}

async function writeBase64AudioToTempFile(label, audioBase64, fileExtension = "audio") {
  const cleanBase64 = String(audioBase64 || "")
    .replace(/^data:audio\/[^;]+;base64,/i, "")
    .trim();

  if (!cleanBase64) {
    throw createHttpError(400, "Missing audioBase64.");
  }

  const extension = safeFileName(fileExtension || "audio");
  const filePath = path.join(
    os.tmpdir(),
    `orange-music-${safeFileName(label)}-${Date.now()}.${extension}`
  );

  await fs.promises.writeFile(filePath, Buffer.from(cleanBase64, "base64"));
  return filePath;
}

function runFpcalc(filePath) {
  return new Promise((resolve, reject) => {
    execFile(
      getFpcalcPath(),
      ["-json", filePath],
      {
        timeout: 120000,
        windowsHide: true
      },
      (error, stdout, stderr) => {
        if (error) {
          reject(
            createHttpError(
              500,
              "Failed to run fpcalc. Make sure Chromaprint is installed and FPCALC_PATH is configured.",
              stderr || error.message
            )
          );
          return;
        }

        try {
          const result = JSON.parse(stdout);
          resolve({
            duration: Math.round(Number(result.duration || 0)),
            fingerprint: String(result.fingerprint || "")
          });
        } catch (parseError) {
          reject(createHttpError(500, "Invalid fpcalc output.", parseError.message));
        }
      }
    );
  });
}

function chunkArray(items, chunkSize) {
  const chunks = [];

  for (let index = 0; index < items.length; index += chunkSize) {
    chunks.push(items.slice(index, index + chunkSize));
  }

  return chunks;
}

function calculateBucketScore(sampleBuckets, candidateBuckets) {
  if (!sampleBuckets.length || !candidateBuckets.length) return 0;

  const candidateSet = new Set(candidateBuckets);
  const matchedCount = sampleBuckets.filter((bucket) => candidateSet.has(bucket)).length;

  return matchedCount / sampleBuckets.length;
}

async function fingerprintTempFile(filePath) {
  const fpcalcResult = await runFpcalc(filePath);

  if (!fpcalcResult.fingerprint) {
    throw createHttpError(422, "fpcalc did not return a fingerprint.");
  }

  return {
    ...fpcalcResult,
    fingerprintHash: buildFingerprintHash(fpcalcResult.fingerprint),
    hashBuckets: buildFingerprintBuckets(fpcalcResult.fingerprint)
  };
}

async function updateSongFingerprintSummary(db, songId, data) {
  await db.collection(COLLECTION_SONGS)
    .doc(songId)
    .set(
      {
        fingerprintStatus: data.status,
        fingerprintAlgorithm: data.algorithm || "",
        fingerprintVersion: data.version || FINGERPRINT_VERSION,
        duplicateOfSongId: data.duplicateOfSongId || "",
        duplicateScore: Number(data.duplicateScore || 0),
        fingerprintError: data.errorMessage || "",
        updatedAt: now()
      },
      {
        merge: true
      }
    );
}

async function markFingerprintStatus(db, songId, status, errorMessage = "") {
  const updatedAt = now();

  await Promise.all([
    db.collection(COLLECTION_AUDIO_FINGERPRINTS)
      .doc(songId)
      .set(
        {
          songId,
          status,
          errorMessage,
          updatedAt,
          createdAt: updatedAt
        },
        {
          merge: true
        }
      ),
    updateSongFingerprintSummary(db, songId, {
      status,
      errorMessage
    })
  ]);
}

async function findExactDuplicate(db, songId, fingerprintHash) {
  if (!fingerprintHash) return null;

  const snapshot = await db.collection(COLLECTION_AUDIO_FINGERPRINTS)
    .where("fingerprintHash", "==", fingerprintHash)
    .limit(10)
    .get();

  const duplicateDoc = snapshot.docs.find((doc) => doc.id !== songId);

  if (!duplicateDoc) return null;

  return {
    songId: duplicateDoc.id,
    data: duplicateDoc.data()
  };
}

async function saveFingerprintResult(db, songId, song, fpcalcResult, duplicate) {
  const status = duplicate
    ? FINGERPRINT_STATUS.DUPLICATE
    : FINGERPRINT_STATUS.UNIQUE;

  const result = {
    songId,
    uploaderId: song.uploaderId || "",
    fingerprint: fpcalcResult.fingerprint,
    fingerprintHash: buildFingerprintHash(fpcalcResult.fingerprint),
    algorithm: FINGERPRINT_ALGORITHM,
    version: FINGERPRINT_VERSION,
    duration: fpcalcResult.duration,
    hashBuckets: buildFingerprintBuckets(fpcalcResult.fingerprint),
    status,
    duplicateOfSongId: duplicate?.songId || "",
    duplicateScore: duplicate ? 1.0 : 0.0,
    errorMessage: "",
    updatedAt: now()
  };

  const existingDoc = await db.collection(COLLECTION_AUDIO_FINGERPRINTS)
    .doc(songId)
    .get();

  result.createdAt = existingDoc.exists && existingDoc.data().createdAt
    ? existingDoc.data().createdAt
    : result.updatedAt;

  await Promise.all([
    db.collection(COLLECTION_AUDIO_FINGERPRINTS)
      .doc(songId)
      .set(result, {
        merge: true
      }),
    updateSongFingerprintSummary(db, songId, result)
  ]);

  return result;
}

async function processSongFingerprint(songId) {
  const finalSongId = String(songId || "").trim();

  if (!finalSongId) {
    throw createHttpError(400, "Missing songId.");
  }

  const db = getDb();
  const songRef = db.collection(COLLECTION_SONGS).doc(finalSongId);
  const songDoc = await songRef.get();

  if (!songDoc.exists) {
    throw createHttpError(404, "Song not found.");
  }

  const song = songDoc.data() || {};
  const songUrl = String(song.songUrl || "").trim();

  if (!songUrl) {
    await markFingerprintStatus(db, finalSongId, FINGERPRINT_STATUS.FAILED, "Missing songUrl.");
    throw createHttpError(400, "Song is missing songUrl.");
  }

  await markFingerprintStatus(db, finalSongId, FINGERPRINT_STATUS.PROCESSING);

  let tempFilePath = "";

  try {
    tempFilePath = await downloadAudioToTempFile(finalSongId, songUrl);
    const fpcalcResult = await runFpcalc(tempFilePath);

    if (!fpcalcResult.fingerprint) {
      throw createHttpError(422, "fpcalc did not return a fingerprint.");
    }

    const duplicate = await findExactDuplicate(
      db,
      finalSongId,
      buildFingerprintHash(fpcalcResult.fingerprint)
    );
    const result = await saveFingerprintResult(db, finalSongId, song, fpcalcResult, duplicate);

    return {
      songId: finalSongId,
      status: result.status,
      duplicateOfSongId: result.duplicateOfSongId,
      duplicateScore: result.duplicateScore,
      algorithm: result.algorithm,
      version: result.version,
      duration: result.duration
    };
  } catch (error) {
    const message = error.detail || error.message || "Fingerprint processing failed.";
    await markFingerprintStatus(db, finalSongId, FINGERPRINT_STATUS.FAILED, message);
    throw error;
  } finally {
    if (tempFilePath) {
      fs.promises.unlink(tempFilePath).catch(() => {});
    }
  }
}

async function createSearchSampleFingerprint(payload) {
  const audioUrl = String(payload.audioUrl || "").trim();
  const audioBase64 = String(payload.audioBase64 || payload.sampleBase64 || "").trim();
  const fileExtension = String(payload.fileExtension || "audio").trim();

  let tempFilePath = "";

  try {
    if (audioUrl) {
      tempFilePath = await downloadAudioToTempFile("search-sample", audioUrl);
    } else if (audioBase64) {
      tempFilePath = await writeBase64AudioToTempFile(
        "search-sample",
        audioBase64,
        fileExtension
      );
    } else {
      throw createHttpError(400, "Missing audioUrl or audioBase64.");
    }

    return await fingerprintTempFile(tempFilePath);
  } finally {
    if (tempFilePath) {
      fs.promises.unlink(tempFilePath).catch(() => {});
    }
  }
}

async function loadSongPreviewMap(db, songIds) {
  const songMap = {};
  const cleanSongIds = [...new Set(songIds.filter(Boolean))];

  for (const songIdChunk of chunkArray(cleanSongIds, 10)) {
    const snapshot = await db.collection(COLLECTION_SONGS)
      .where(FieldPath.documentId(), "in", songIdChunk)
      .get();

    snapshot.docs.forEach((doc) => {
      const song = doc.data() || {};
      songMap[doc.id] = {
        id: doc.id,
        title: song.title || "",
        artist: song.artist || "",
        coverUrl: song.coverUrl || "",
        songUrl: song.songUrl || "",
        duration: Number(song.duration || 0),
        uploaderId: song.uploaderId || "",
        genre: song.genre || "",
        status: song.status || "",
        fingerprintStatus: song.fingerprintStatus || ""
      };
    });
  }

  return songMap;
}

async function findAudioMatches(db, sampleFingerprint, limit) {
  const finalLimit = Math.max(1, Math.min(Number(limit || 5), 10));
  const candidates = new Map();

  const exactSnapshot = await db.collection(COLLECTION_AUDIO_FINGERPRINTS)
    .where("fingerprintHash", "==", sampleFingerprint.fingerprintHash)
    .limit(finalLimit)
    .get();

  exactSnapshot.docs.forEach((doc) => {
    const data = doc.data() || {};
    candidates.set(doc.id, {
      songId: doc.id,
      matchType: "exact",
      score: 1.0,
      fingerprintStatus: data.status || "",
      fingerprintDuration: Number(data.duration || 0)
    });
  });

  const sampleBuckets = sampleFingerprint.hashBuckets || [];
  const bucketChunks = chunkArray(sampleBuckets.slice(0, 40), 10);

  for (const bucketChunk of bucketChunks) {
    if (!bucketChunk.length) continue;

    const bucketSnapshot = await db.collection(COLLECTION_AUDIO_FINGERPRINTS)
      .where("hashBuckets", "array-contains-any", bucketChunk)
      .limit(50)
      .get();

    bucketSnapshot.docs.forEach((doc) => {
      const data = doc.data() || {};
      const score = calculateBucketScore(sampleBuckets, data.hashBuckets || []);
      const existing = candidates.get(doc.id);

      if (existing && existing.score >= score) return;

      candidates.set(doc.id, {
        songId: doc.id,
        matchType: existing?.matchType || "bucket_overlap",
        score,
        fingerprintStatus: data.status || "",
        fingerprintDuration: Number(data.duration || 0)
      });
    });
  }

  const matches = [...candidates.values()]
    .filter((match) => match.score > 0)
    .sort((left, right) => right.score - left.score)
    .slice(0, finalLimit);

  const songMap = await loadSongPreviewMap(
    db,
    matches.map((match) => match.songId)
  );

  return matches.map((match) => ({
    ...match,
    score: Number(match.score.toFixed(4)),
    song: songMap[match.songId] || null
  }));
}

async function searchAudioFingerprint(payload) {
  const db = getDb();
  const sampleFingerprint = await createSearchSampleFingerprint(payload || {});
  const matches = await findAudioMatches(db, sampleFingerprint, payload?.limit);

  return {
    sample: {
      duration: sampleFingerprint.duration,
      algorithm: FINGERPRINT_ALGORITHM,
      version: FINGERPRINT_VERSION,
      hashBucketCount: sampleFingerprint.hashBuckets.length
    },
    matches
  };
}

module.exports = {
  processSongFingerprint,
  searchAudioFingerprint,
  getFpcalcPath,
  FINGERPRINT_STATUS
};
