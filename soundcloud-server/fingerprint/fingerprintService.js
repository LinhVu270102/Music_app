const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFile } = require("child_process");

const axios = require("axios");

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

async function downloadAudioToTempFile(songId, songUrl) {
  const filePath = path.join(os.tmpdir(), `orange-music-${safeFileName(songId)}-${Date.now()}.audio`);
  const writer = fs.createWriteStream(filePath);

  const response = await axios.get(songUrl, {
    responseType: "stream",
    timeout: 60000,
    maxRedirects: 5,
    validateStatus: (status) => status >= 200 && status < 300
  });

  await new Promise((resolve, reject) => {
    response.data.pipe(writer);
    response.data.on("error", reject);
    writer.on("finish", resolve);
    writer.on("error", reject);
  });

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

async function findExactDuplicate(db, songId, fingerprint) {
  const snapshot = await db.collection(COLLECTION_AUDIO_FINGERPRINTS)
    .where("fingerprint", "==", fingerprint)
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

    const duplicate = await findExactDuplicate(db, finalSongId, fpcalcResult.fingerprint);
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

module.exports = {
  processSongFingerprint,
  getFpcalcPath,
  FINGERPRINT_STATUS
};
