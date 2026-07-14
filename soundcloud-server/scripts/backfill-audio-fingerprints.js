const path = require("path");

require("dotenv").config({
  path: path.join(__dirname, "..", ".env")
});

const { getDb } = require("../fingerprint/firebaseAdmin");
const {
  FINGERPRINT_STATUS,
  processSongFingerprint
} = require("../fingerprint/fingerprintService");

const DEFAULT_LIMIT = 20;
const DEFAULT_DELAY_MS = 500;

function parseArgs(argv) {
  const args = {
    write: false,
    limit: DEFAULT_LIMIT,
    delayMs: DEFAULT_DELAY_MS,
    onlyId: "",
    all: false,
    includeFailed: false
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    const next = argv[index + 1];

    if (arg === "--write") args.write = true;
    if (arg === "--dry-run") args.write = false;
    if (arg === "--all") args.all = true;
    if (arg === "--include-failed") args.includeFailed = true;

    if (arg === "--limit" && next) {
      args.limit = Number(next);
      index += 1;
    }

    if (arg === "--delay-ms" && next) {
      args.delayMs = Number(next);
      index += 1;
    }

    if (arg === "--only-id" && next) {
      args.onlyId = next;
      index += 1;
    }
  }

  if (Number.isNaN(args.limit) || args.limit < 1) args.limit = DEFAULT_LIMIT;
  if (Number.isNaN(args.delayMs) || args.delayMs < 0) args.delayMs = DEFAULT_DELAY_MS;

  return args;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function normalizeStatus(value) {
  return String(value || "").trim().toLowerCase();
}

function shouldBackfillSong(song, args) {
  if (!song || song.isDeleted === true) return false;
  if (!String(song.songUrl || "").trim()) return false;
  if (args.all) return true;

  const status = normalizeStatus(song.fingerprintStatus);

  if (!status || status === FINGERPRINT_STATUS.PENDING) return true;
  if (args.includeFailed && status === FINGERPRINT_STATUS.FAILED) return true;

  return false;
}

async function loadOnlySong(db, songId, args) {
  const doc = await db.collection("songs").doc(songId).get();

  if (!doc.exists) {
    return [];
  }

  const song = doc.data();

  return shouldBackfillSong(song, args)
    ? [{ id: doc.id, song }]
    : [];
}

async function loadCandidateSongs(db, args) {
  if (args.onlyId) {
    return loadOnlySong(db, args.onlyId, args);
  }

  const snapshot = await db.collection("songs").get();
  const candidates = [];

  for (const doc of snapshot.docs) {
    const song = doc.data();

    if (!shouldBackfillSong(song, args)) continue;

    candidates.push({
      id: doc.id,
      song
    });

    if (candidates.length >= args.limit) break;
  }

  return candidates;
}

function printDryRun(candidates, args) {
  console.log("Audio fingerprint backfill dry-run");
  console.log({
    candidates: candidates.length,
    limit: args.limit,
    onlyId: args.onlyId || null,
    all: args.all,
    includeFailed: args.includeFailed
  });

  candidates.slice(0, 20).forEach((item, index) => {
    console.log(
      `${index + 1}. ${item.id} | ${item.song.title || "(untitled)"} | ` +
        `fingerprintStatus=${item.song.fingerprintStatus || "(missing)"}`
    );
  });

  console.log("");
  console.log("Run with --write to process these songs.");
}

async function processCandidates(candidates, args) {
  const summary = {
    total: candidates.length,
    processed: 0,
    unique: 0,
    duplicate: 0,
    failed: 0
  };

  for (const item of candidates) {
    try {
      console.log(`[start] ${item.id} - ${item.song.title || "(untitled)"}`);

      const result = await processSongFingerprint(item.id);

      summary.processed += 1;

      if (result.status === FINGERPRINT_STATUS.UNIQUE) summary.unique += 1;
      if (result.status === FINGERPRINT_STATUS.DUPLICATE) summary.duplicate += 1;

      console.log(
        `[done] ${item.id}: status=${result.status}, ` +
          `duplicateOf=${result.duplicateOfSongId || "-"}, score=${result.duplicateScore || 0}`
      );
    } catch (error) {
      summary.failed += 1;
      console.error(`[fail] ${item.id}: ${error.detail || error.message}`);
    }

    if (args.delayMs > 0) {
      await sleep(args.delayMs);
    }
  }

  return summary;
}

async function run() {
  const args = parseArgs(process.argv.slice(2));
  const db = getDb();
  const candidates = await loadCandidateSongs(db, args);

  if (!args.write) {
    printDryRun(candidates, args);
    return;
  }

  const summary = await processCandidates(candidates, args);

  console.log("Audio fingerprint backfill complete");
  console.log(summary);
}

run().catch((error) => {
  console.error("Audio fingerprint backfill failed:", error);
  process.exit(1);
});
