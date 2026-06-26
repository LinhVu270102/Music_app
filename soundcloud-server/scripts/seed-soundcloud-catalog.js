const path = require("path");

const axios = require("axios");
const { cert, initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const SERVICE_ACCOUNT_PATH =
  process.env.GOOGLE_APPLICATION_CREDENTIALS ||
  path.join(__dirname, "..", "serviceAccountKey.json");

const SOUND_CLOUD_API_BASE_URL =
  process.env.SOUNDCLOUD_API_BASE_URL || "http://127.0.0.1:3000";

const TARGET_TRACK_COUNT = 100;
const TARGET_PLAYLIST_COUNT = 100;
const MIN_SONGS_PER_PLAYLIST = 10;
const MAX_SONGS_PER_PLAYLIST = 20;
const BATCH_LIMIT = 400;
const SEED_OWNER_ID = "soundcloud_api_playlist";

const SEARCH_QUERIES = [
  "pop",
  "rock",
  "hip hop",
  "electronic",
  "chill",
  "jazz",
  "indie",
  "ambient",
  "dance",
  "classical"
];

const PLAYLIST_MOODS = [
  "Midnight",
  "Golden",
  "Neon",
  "Velvet",
  "Weekend",
  "Cloudy",
  "Sunset",
  "Electric",
  "Dreamy",
  "Afterglow"
];

const PLAYLIST_THEMES = [
  "Drive",
  "Focus",
  "Escape",
  "Vibes",
  "Rhythm",
  "Session",
  "Moments",
  "Stories",
  "Mix",
  "Flow"
];

if (!require("fs").existsSync(SERVICE_ACCOUNT_PATH)) {
  console.error("Missing service account key:", SERVICE_ACCOUNT_PATH);
  process.exit(1);
}

initializeApp({
  credential: cert(require(SERVICE_ACCOUNT_PATH))
});

const db = getFirestore();

let batch = db.batch();
let operationCount = 0;

async function commitBatch(force = false) {
  if (operationCount === 0 || (!force && operationCount < BATCH_LIMIT)) return;

  await batch.commit();
  console.log(`Committed ${operationCount} Firestore operations`);
  batch = db.batch();
  operationCount = 0;
}

async function queueSet(ref, data) {
  batch.set(ref, data, { merge: true });
  operationCount += 1;
  await commitBatch();
}

async function queueDelete(ref) {
  batch.delete(ref);
  operationCount += 1;
  await commitBatch();
}

async function fetchTracks() {
  const tracksById = new Map();

  for (const query of SEARCH_QUERIES) {
    const response = await axios.get(
      `${SOUND_CLOUD_API_BASE_URL}/searchSoundCloudTracks`,
      { params: { q: query, limit: 20 }, timeout: 30_000 }
    );

    const results = Array.isArray(response.data?.results) ? response.data.results : [];
    console.log(`Query "${query}" returned ${results.length} playable tracks`);

    for (const track of results) {
      if (track?.id && track?.title) tracksById.set(track.id, track);
    }

    if (tracksById.size >= TARGET_TRACK_COUNT) break;
  }

  const tracks = Array.from(tracksById.values()).slice(0, TARGET_TRACK_COUNT);

  if (tracks.length < TARGET_TRACK_COUNT) {
    throw new Error(
      `Only ${tracks.length} unique SoundCloud tracks were available; need ${TARGET_TRACK_COUNT}.`
    );
  }

  return tracks;
}

function normalizeSong(track, now) {
  return {
    id: track.id,
    title: track.title || "Untitled",
    artist: track.artist || track.uploaderName || "SoundCloud Artist",
    coverUrl: track.coverUrl || track.uploaderAvatarUrl || "",
    songUrl: "",
    duration: Number(track.duration || 0),
    plays: Number(track.plays || track.playbackCount || 0),
    likes: Number(track.likes || track.likesCount || 0),
    commentsCount: Number(track.commentsCount || 0),
    reportsCount: 0,
    uploaderId: track.uploaderId || "soundcloud",
    genre: track.genre || "",
    tags: ["soundcloud", track.genre || ""].filter(Boolean),
    // Must match SongStatus.APPROVED and Firestore Rules exactly.
    status: "approved",
    rejectReason: "",
    reviewedBy: "soundcloud_catalog_seed",
    reviewedAt: now,
    allowComments: true,
    isDeleted: false,
    deletedAt: 0,
    deletedBy: "",
    createdAt: now,
    updatedAt: now,
    source: "soundcloud",
    sourceLabel: "SoundCloud",
    seedType: "soundcloud_catalog",
    soundCloudId: Number(track.soundCloudId || String(track.id).replace("soundcloud_", "")) || 0,
    permalinkUrl: track.permalinkUrl || "",
    streamable: track.streamable === true,
    access: track.access || "playable"
  };
}

function createArtistProfile(track, profileStats, now) {
  const artistId = track.uploaderId || `soundcloud_artist_${safeId(track.artist)}`;
  const existing = profileStats.get(artistId) || {
    trackCount: 0,
    genres: new Set()
  };

  existing.trackCount += 1;
  if (track.genre) existing.genres.add(track.genre);
  profileStats.set(artistId, existing);

  return {
    uid: artistId,
    email: "",
    displayName: track.artist || track.uploaderName || "SoundCloud Artist",
    username: track.uploaderUsername || safeId(track.artist) || artistId,
    avatarUrl: track.uploaderAvatarUrl || track.coverUrl || "",
    bio: "Artist profile generated from the SoundCloud catalog.",
    fullName: track.artist || track.uploaderName || "SoundCloud Artist",
    phoneNumber: "",
    dateOfBirth: 0,
    gender: "",
    country: "",
    favoriteGenres: track.genre ? [track.genre] : [],
    musicMoodTags: ["soundcloud"],
    role: "USER",
    accountStatus: "ACTIVE",
    likedSongsCount: 0,
    playlistsCount: 0,
    followersCount: 0,
    followingCount: 0,
    uploadedSongsCount: existing.trackCount,
    createdAt: now,
    updatedAt: now,
    lastLoginAt: 0,
    source: "soundcloud",
    seedType: "soundcloud_catalog"
  };
}

function createPlaylist(index, songs, now) {
  const songCount = MIN_SONGS_PER_PLAYLIST +
    (index % (MAX_SONGS_PER_PLAYLIST - MIN_SONGS_PER_PLAYLIST + 1));
  const selectedSongs = [];

  for (let offset = 0; offset < songCount; offset += 1) {
    const songIndex = (index * 13 + offset * 7) % songs.length;
    selectedSongs.push(songs[songIndex]);
  }

  const number = String(index + 1).padStart(3, "0");
  const mood = PLAYLIST_MOODS[index % PLAYLIST_MOODS.length];
  const theme = PLAYLIST_THEMES[Math.floor(index / PLAYLIST_MOODS.length) % PLAYLIST_THEMES.length];
  const genres = Array.from(
    new Set(selectedSongs.map((song) => song.genre).filter(Boolean))
  ).slice(0, 3);
  const songKeywords = selectedSongs
    .slice(0, 3)
    .map((song) => `${song.title} ${song.artist}`)
    .join(" · ");

  return {
    id: `soundcloud_seed_playlist_${number}`,
    name: `${genres[0] || "SoundCloud"} · ${mood} ${theme} ${number}`,
    description: [
      `SoundCloud ${genres.join(" ")} playlist with ${selectedSongs.length} playable tracks.`,
      songKeywords
    ].filter(Boolean).join(" — "),
    coverUrl: selectedSongs[0]?.coverUrl || "",
    ownerId: SEED_OWNER_ID,
    isPublic: true,
    songsCount: selectedSongs.length,
    createdAt: now,
    updatedAt: now,
    source: "soundcloud",
    sourceLabel: "SoundCloud",
    seedType: "soundcloud_catalog"
  };
}

function safeId(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 80);
}

async function replacePlaylistSongs(playlist, songs) {
  const playlistRef = db.collection("playlists").doc(playlist.id);
  const oldSongs = await playlistRef.collection("songs").get();

  for (const oldSong of oldSongs.docs) {
    await queueDelete(oldSong.ref);
  }

  await queueSet(playlistRef, playlist);

  for (const song of songs) {
    await queueSet(
      playlistRef.collection("songs").doc(song.id),
      { ...song, addedAt: playlist.updatedAt }
    );
  }
}

async function pruneStaleSeedDocuments(collectionName, currentIds) {
  const existing = await db
    .collection(collectionName)
    .where("seedType", "==", "soundcloud_catalog")
    .get();

  for (const document of existing.docs) {
    if (!currentIds.has(document.id)) {
      await queueDelete(document.ref);
    }
  }
}

async function main() {
  console.log("Fetching SoundCloud tracks from:", SOUND_CLOUD_API_BASE_URL);
  const rawTracks = await fetchTracks();
  const now = Date.now();
  const songs = rawTracks.map((track) => normalizeSong(track, now));
  const profileStats = new Map();
  const profiles = rawTracks.map((track) => createArtistProfile(track, profileStats, now));

  for (const song of songs) {
    await queueSet(db.collection("songs").doc(song.id), song);
  }

  const profilesById = new Map();
  for (const profile of profiles) {
    const stats = profileStats.get(profile.uid);
    profilesById.set(profile.uid, {
      ...profile,
      uploadedSongsCount: stats?.trackCount || 0,
      favoriteGenres: Array.from(stats?.genres || [])
    });
  }

  for (const profile of profilesById.values()) {
    await queueSet(db.collection("users").doc(profile.uid), profile);
  }

  await pruneStaleSeedDocuments("songs", new Set(songs.map((song) => song.id)));
  await pruneStaleSeedDocuments("users", new Set(profilesById.keys()));

  for (let index = 0; index < TARGET_PLAYLIST_COUNT; index += 1) {
    const playlist = createPlaylist(index, songs, now);
    const playlistSongs = [];

    for (let offset = 0; offset < playlist.songsCount; offset += 1) {
      playlistSongs.push(songs[(index * 13 + offset * 7) % songs.length]);
    }

    await replacePlaylistSongs(playlist, playlistSongs);
  }

  await commitBatch(true);

  console.log("Seed completed", {
    songs: songs.length,
    artistProfiles: profilesById.size,
    playlists: TARGET_PLAYLIST_COUNT
  });
}

main().catch((error) => {
  console.error("Catalog seed failed:", error.response?.data || error.message || error);
  process.exit(1);
});
