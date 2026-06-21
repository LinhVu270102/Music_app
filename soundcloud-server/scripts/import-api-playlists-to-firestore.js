const fs = require("fs");
const path = require("path");
const axios = require("axios");

const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const SERVICE_ACCOUNT_PATH =
  process.env.GOOGLE_APPLICATION_CREDENTIALS ||
  path.join(__dirname, "..", "serviceAccountKey.json");

const PLAYLISTS_FILE =
  process.env.PLAYLISTS_FILE ||
  path.join(__dirname, "..", "output", "soundcloud_api_playlists_100.json");

const PLAYLISTS_URL = process.env.PLAYLISTS_URL || "";

const COLLECTION_NAME = "playlists";
const SOUNDCLOUD_API_PLAYLIST_OWNER_ID = "soundcloud_api_playlist";
const BATCH_LIMIT = 450;

if (!fs.existsSync(SERVICE_ACCOUNT_PATH)) {
  console.error("Missing service account key:", SERVICE_ACCOUNT_PATH);
  process.exit(1);
}

const serviceAccount = require(SERVICE_ACCOUNT_PATH);

initializeApp({
  credential: cert(serviceAccount),
});

const db = getFirestore();

function normalizeString(value) {
  if (value === null || value === undefined) return "";
  return String(value).trim();
}

function normalizeNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function pickFirstString(item, keys) {
  for (const key of keys) {
    const value = normalizeString(item[key]);
    if (value) return value;
  }

  return "";
}

function extractArray(rawData) {
  if (Array.isArray(rawData)) return rawData;

  if (Array.isArray(rawData.playlists)) return rawData.playlists;
  if (Array.isArray(rawData.results)) return rawData.results;
  if (Array.isArray(rawData.data)) return rawData.data;
  if (Array.isArray(rawData.items)) return rawData.items;

  return [];
}

function buildFirestorePlaylist(item) {
  const rawId = pickFirstString(item, [
    "id",
    "playlistId",
    "soundCloudId",
    "soundcloudId",
    "urn",
    "permalink_url",
  ]);

  const name = pickFirstString(item, [
    "name",
    "title",
    "playlistName",
  ]);

  if (!rawId || !name) return null;

  const cleanRawId = rawId
    .replace("soundcloud_api_playlist_", "")
    .replace("soundcloud_playlist_", "")
    .replace(/[^\w-]/g, "_");

  const playlistId = `soundcloud_api_playlist_${cleanRawId}`;

  const coverUrl = pickFirstString(item, [
    "coverUrl",
    "artworkUrl",
    "artwork_url",
    "image",
    "thumbnail",
  ]);

  const description = pickFirstString(item, [
    "description",
    "userName",
    "username",
    "creator",
  ]);

  const songsCount =
    normalizeNumber(item.songsCount) ||
    normalizeNumber(item.trackCount) ||
    normalizeNumber(item.track_count) ||
    normalizeNumber(Array.isArray(item.tracks) ? item.tracks.length : 0);

  const now = Date.now();

  return {
    id: playlistId,
    name,
    description,
    coverUrl,
    ownerId: SOUNDCLOUD_API_PLAYLIST_OWNER_ID,
    isPublic: true,
    songsCount,
    source: "soundcloud",
    sourceId: rawId,
    createdAt: item.createdAt || now,
    updatedAt: now,
  };
}

async function loadPlaylists() {
  if (PLAYLISTS_URL) {
    console.log("Loading playlists from URL:", PLAYLISTS_URL);

    const response = await axios.get(PLAYLISTS_URL);
    return extractArray(response.data);
  }

  if (!fs.existsSync(PLAYLISTS_FILE)) {
    console.error("Missing playlists file:", PLAYLISTS_FILE);
    console.error("Set PLAYLISTS_FILE or PLAYLISTS_URL before running this script.");
    process.exit(1);
  }

  console.log("Loading playlists from file:", PLAYLISTS_FILE);

  const raw = fs.readFileSync(PLAYLISTS_FILE, "utf8");
  return extractArray(JSON.parse(raw));
}

async function commitBatch(batch, count) {
  if (count <= 0) return;

  await batch.commit();
  console.log(`Committed ${count} playlists`);
}

async function importPlaylists() {
  const rawPlaylists = await loadPlaylists();

  const playlists = rawPlaylists
    .map(buildFirestorePlaylist)
    .filter(Boolean);

  const uniquePlaylists = Array.from(
    new Map(playlists.map((playlist) => [playlist.id, playlist])).values()
  );

  console.log(`Found ${rawPlaylists.length} raw playlists`);
  console.log(`Importing ${uniquePlaylists.length} valid playlists`);

  let batch = db.batch();
  let operationCount = 0;
  let totalImported = 0;

  for (const playlist of uniquePlaylists) {
    const ref = db.collection(COLLECTION_NAME).doc(playlist.id);

    batch.set(ref, playlist, { merge: true });

    operationCount += 1;
    totalImported += 1;

    if (operationCount >= BATCH_LIMIT) {
      await commitBatch(batch, operationCount);
      batch = db.batch();
      operationCount = 0;
    }
  }

  await commitBatch(batch, operationCount);

  console.log(`Done. Total imported: ${totalImported}`);
  process.exit(0);
}

importPlaylists().catch((error) => {
  console.error("Import failed:", error);
  process.exit(1);
});