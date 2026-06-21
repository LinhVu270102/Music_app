const fs = require("fs");
const path = require("path");

const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const SERVICE_ACCOUNT_PATH =
  process.env.GOOGLE_APPLICATION_CREDENTIALS ||
  path.join(__dirname, "..", "serviceAccountKey.json");

const PLAYLIST_OWNER_ID = "soundcloud_api_playlist";

const PLAYLIST_COLLECTION = "playlists";
const SONG_COLLECTION = "songs";

const MIN_SONGS_PER_PLAYLIST = Number(process.env.MIN_SONGS_PER_PLAYLIST || 10);
const MAX_SONGS_PER_PLAYLIST = Number(process.env.MAX_SONGS_PER_PLAYLIST || 20);

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

let batch = db.batch();
let operationCount = 0;

async function commitBatchIfNeeded(force = false) {
  if (operationCount === 0) return;

  if (force || operationCount >= BATCH_LIMIT) {
    await batch.commit();
    console.log(`Committed ${operationCount} operations`);

    batch = db.batch();
    operationCount = 0;
  }
}

async function queueSet(ref, data, options = { merge: true }) {
  batch.set(ref, data, options);
  operationCount += 1;
  await commitBatchIfNeeded();
}

async function queueUpdate(ref, data) {
  batch.update(ref, data);
  operationCount += 1;
  await commitBatchIfNeeded();
}

async function queueDelete(ref) {
  batch.delete(ref);
  operationCount += 1;
  await commitBatchIfNeeded();
}

function getRandomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function shuffleArray(array) {
  return [...array].sort(() => Math.random() - 0.5);
}

function normalizeSong(doc) {
  const data = doc.data();

  return {
    id: doc.id,
    ...data,
  };
}

function isValidSong(song) {
  if (!song.id) return false;

  const isDeleted = song.isDeleted === true;
  if (isDeleted) return false;

  const status = String(song.status || "").toUpperCase();
  if (status && status !== "APPROVED") return false;

  const hasPlayableSource =
    Boolean(song.songUrl) ||
    String(song.id).startsWith("soundcloud_") ||
    String(song.source || "").toLowerCase().includes("soundcloud");

  return hasPlayableSource;
}

function pickCoverUrl(songs) {
  const songsWithCover = songs.filter((song) => Boolean(song.coverUrl));

  if (songsWithCover.length === 0) return "";

  const randomSong = songsWithCover[getRandomInt(0, songsWithCover.length - 1)];

  return randomSong.coverUrl || "";
}

async function getImportedPlaylists() {
  const snapshot = await db
    .collection(PLAYLIST_COLLECTION)
    .where("ownerId", "==", PLAYLIST_OWNER_ID)
    .get();

  return snapshot.docs.map((doc) => ({
    id: doc.id,
    ref: doc.ref,
    ...doc.data(),
  }));
}

async function getAvailableSongs() {
  const snapshot = await db.collection(SONG_COLLECTION).get();

  return snapshot.docs
    .map(normalizeSong)
    .filter(isValidSong);
}

async function clearPlaylistSongs(playlistId) {
  const songsSnapshot = await db
    .collection(PLAYLIST_COLLECTION)
    .doc(playlistId)
    .collection("songs")
    .get();

  for (const doc of songsSnapshot.docs) {
    await queueDelete(doc.ref);
  }
}

async function populatePlaylist(playlist, allSongs) {
  const numberOfSongs = getRandomInt(
    MIN_SONGS_PER_PLAYLIST,
    MAX_SONGS_PER_PLAYLIST
  );

  const selectedSongs = shuffleArray(allSongs).slice(0, numberOfSongs);

  if (selectedSongs.length === 0) {
    console.log(`Skipped playlist ${playlist.id}: no songs`);
    return;
  }

  await clearPlaylistSongs(playlist.id);

  for (const song of selectedSongs) {
    const songRef = db
      .collection(PLAYLIST_COLLECTION)
      .doc(playlist.id)
      .collection("songs")
      .doc(song.id);

    await queueSet(songRef, {
      ...song,
      addedAt: Date.now(),
    });
  }

  const coverUrl = pickCoverUrl(selectedSongs);

  await queueUpdate(playlist.ref, {
    coverUrl,
    songsCount: selectedSongs.length,
    updatedAt: Date.now(),
  });

  console.log(
    `Populated ${playlist.name || playlist.id}: songs=${selectedSongs.length}`
  );
}

async function main() {
  const playlists = await getImportedPlaylists();
  const songs = await getAvailableSongs();

  console.log(`Found playlists: ${playlists.length}`);
  console.log(`Found available songs: ${songs.length}`);

  if (playlists.length === 0) {
    console.error("No imported playlists found.");
    process.exit(1);
  }

  if (songs.length === 0) {
    console.error("No available songs found in Firestore songs collection.");
    process.exit(1);
  }

  for (const playlist of playlists) {
    await populatePlaylist(playlist, songs);
  }

  await commitBatchIfNeeded(true);

  console.log("Done populating playlists with random songs.");
  process.exit(0);
}

main().catch((error) => {
  console.error("Populate failed:", error);
  process.exit(1);
});