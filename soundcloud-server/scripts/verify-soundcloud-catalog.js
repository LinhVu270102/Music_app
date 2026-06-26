const path = require("path");

const { cert, initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const SERVICE_ACCOUNT_PATH =
  process.env.GOOGLE_APPLICATION_CREDENTIALS ||
  path.join(__dirname, "..", "serviceAccountKey.json");

initializeApp({
  credential: cert(require(SERVICE_ACCOUNT_PATH))
});

async function main() {
  const db = getFirestore();
  const [songs, profiles, playlists, publicPlaylistIndex] = await Promise.all([
    db.collection("songs").where("seedType", "==", "soundcloud_catalog").get(),
    db.collection("users").where("seedType", "==", "soundcloud_catalog").get(),
    db.collection("playlists").where("seedType", "==", "soundcloud_catalog").get(),
    db.collection("playlists").where("isPublic", "==", true).get()
  ]);

  const playlistSongEntries = playlists.docs.reduce(
    (sum, playlist) => sum + Number(playlist.get("songsCount") || 0),
    0
  );

  const playlistCounts = await Promise.all(
    playlists.docs.map(async (playlist) => {
      const songs = await playlist.ref.collection("songs").get();
      const visibleSongs = songs.docs.filter((song) => {
        return song.get("status") === "approved" && song.get("isDeleted") !== true;
      });

      return {
        id: playlist.id,
        expected: Number(playlist.get("songsCount") || 0),
        actual: songs.size,
        visible: visibleSongs.length
      };
    })
  );

  const countMismatches = playlistCounts.filter((playlist) => {
    return playlist.expected !== playlist.actual || playlist.expected !== playlist.visible;
  });

  const allPublicPlaylistCounts = await Promise.all(
    publicPlaylistIndex.docs.map(async (playlist) => {
      const songs = await playlist.ref.collection("songs").get();
      const visible = songs.docs.filter((song) => {
        return song.get("status") === "approved" && song.get("isDeleted") !== true;
      }).length;

      return {
        id: playlist.id,
        seedType: playlist.get("seedType") || "",
        ownerId: playlist.get("ownerId") || "",
        expected: Number(playlist.get("songsCount") || 0),
        actual: songs.size,
        visible
      };
    })
  );

  const publicCountMismatches = allPublicPlaylistCounts.filter((playlist) => {
    return playlist.expected !== playlist.visible;
  });

  console.log(JSON.stringify({
    soundCloudSongs: songs.size,
    soundCloudArtistProfiles: profiles.size,
    seededPlaylists: playlists.size,
    playlistSongEntries,
    actualPlaylistSongEntries: playlistCounts.reduce((sum, playlist) => sum + playlist.actual, 0),
    playlistsWithCountMismatch: countMismatches.length,
    countMismatchSamples: countMismatches.slice(0, 5),
    publicPlaylistsVisibleToSearchQuery: publicPlaylistIndex.size,
    publicPlaylistsWithRootSongCountMismatch: publicCountMismatches.length,
    publicCountMismatchSamples: publicCountMismatches.slice(0, 5),
    sampleSeededPlaylistNames: playlists.docs.slice(0, 5).map((playlist) => playlist.get("name"))
  }, null, 2));
}

main().catch((error) => {
  console.error("Catalog verification failed:", error.message || error);
  process.exit(1);
});
