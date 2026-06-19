const admin = require("firebase-admin");
const fs = require("fs");
const path = require("path");

const DEMO_SEED_ID = "orange_music_demo_v1";
const STATE_FILE = path.join(__dirname, "demo-seed-state.json");

const serviceAccountPath =
  process.env.GOOGLE_APPLICATION_CREDENTIALS ||
  path.join(__dirname, "serviceAccountKey.json");

if (!fs.existsSync(serviceAccountPath)) {
  console.error("Missing service account file.");
  console.error("Put serviceAccountKey.json in tools/firebase-seed/");
  console.error("or set GOOGLE_APPLICATION_CREDENTIALS.");
  process.exit(1);
}

admin.initializeApp({
  credential: admin.credential.cert(require(serviceAccountPath))
});

const auth = admin.auth();
const db = admin.firestore();

const now = () => Date.now();

const demoAdmins = [
  {
    email: "admin1@orangemusic.demo",
    password: "Demo@123456",
    displayName: "Orange Admin 1",
    role: "ADMIN"
  },
  {
    email: "admin2@orangemusic.demo",
    password: "Demo@123456",
    displayName: "Orange Admin 2",
    role: "ADMIN"
  }
];

const demoUsers = Array.from({ length: 20 }).map((_, index) => {
  const number = String(index + 1).padStart(2, "0");

  return {
    email: `user${number}@orangemusic.demo`,
    password: "Demo@123456",
    displayName: `Demo User ${number}`,
    role: "USER"
  };
});

const songSamples = [
  {
    id: "demo_song_01",
    title: "Morning Chill",
    artist: "Orange Studio",
    genre: "Chill",
    status: "APPROVED",
    reportsCount: 0
  },
  {
    id: "demo_song_02",
    title: "Night Drive",
    artist: "City Beats",
    genre: "Electronic",
    status: "APPROVED",
    reportsCount: 1
  },
  {
    id: "demo_song_03",
    title: "Lost In Hanoi",
    artist: "Lofi VN",
    genre: "Lo-fi",
    status: "PENDING",
    reportsCount: 0
  },
  {
    id: "demo_song_04",
    title: "Summer Upload",
    artist: "Young Artist",
    genre: "Pop",
    status: "PENDING",
    reportsCount: 0
  },
  {
    id: "demo_song_05",
    title: "Rejected Noise",
    artist: "Unknown Demo",
    genre: "Experimental",
    status: "REJECTED",
    reportsCount: 0,
    rejectReason: "Audio quality is too low."
  },
  {
    id: "demo_song_06",
    title: "Reported Track",
    artist: "Report Test",
    genre: "Hip Hop",
    status: "APPROVED",
    reportsCount: 2
  },
  {
    id: "demo_song_07",
    title: "Hidden Old Track",
    artist: "Old Demo",
    genre: "Rock",
    status: "APPROVED",
    reportsCount: 0,
    isDeleted: true
  },
  {
    id: "demo_song_08",
    title: "Pending Copyright Check",
    artist: "Sample Artist",
    genre: "R&B",
    status: "PENDING",
    reportsCount: 1
  }
];

const audioUrls = [
  "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
  "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
  "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
  "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
  "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
  "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3"
];

function loadState() {
  if (!fs.existsSync(STATE_FILE)) {
    return {
      authUsers: [],
      firestoreDocs: []
    };
  }

  return JSON.parse(fs.readFileSync(STATE_FILE, "utf8"));
}

function saveState(state) {
  fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2), "utf8");
}

async function createOrUpdateAuthUser(account) {
  try {
    const existing = await auth.getUserByEmail(account.email);

    await auth.updateUser(existing.uid, {
      displayName: account.displayName,
      password: account.password
    });

    return existing.uid;
  } catch (error) {
    if (error.code !== "auth/user-not-found") {
      throw error;
    }

    const created = await auth.createUser({
      email: account.email,
      password: account.password,
      displayName: account.displayName,
      emailVerified: true
    });

    return created.uid;
  }
}

async function seedUsers(state) {
  const allAccounts = [...demoAdmins, ...demoUsers];

  for (const account of allAccounts) {
    const uid = await createOrUpdateAuthUser(account);

    const userDoc = {
      uid,
      email: account.email,
      displayName: account.displayName,
      avatarUrl: "",
      bio: "Demo account for Orange Music graduation project.",
      role: account.role,
      followersCount: 0,
      followingCount: 0,
      songsCount: 0,
      createdAt: now(),
      updatedAt: now(),
      demoSeedId: DEMO_SEED_ID
    };

    await db.collection("users").doc(uid).set(userDoc, { merge: true });

    state.authUsers.push(uid);
    state.firestoreDocs.push(`users/${uid}`);

    console.log(`Seeded user: ${account.email} - ${account.role}`);
  }
}

function createSongDoc(sample, index, uploaderId) {
  const created = now() - index * 3600 * 1000;
  const isDeleted = sample.isDeleted === true;

  return {
    id: sample.id,
    title: sample.title,
    artist: sample.artist,
    coverUrl: `https://picsum.photos/seed/${sample.id}/400/400`,
    songUrl: audioUrls[index % audioUrls.length],
    duration: 240000,

    plays: 1000 + index * 125,
    likes: 10 + index * 3,
    commentsCount: 0,
    reportsCount: sample.reportsCount || 0,

    uploaderId,
    genre: sample.genre,
    tags: ["demo", sample.genre.toLowerCase(), "orange"],

    status: sample.status,
    rejectReason: sample.rejectReason || "",
    reviewedBy: sample.status === "PENDING" ? "" : "system",
    reviewedAt: sample.status === "PENDING" ? 0 : now(),

    allowComments: true,

    isDeleted,
    deletedBy: isDeleted ? "system" : "",
    deletedAt: isDeleted ? now() : 0,

    createdAt: created,
    updatedAt: now(),

    source: "firebase",
    soundCloudId: 0,
    permalinkUrl: "",
    streamable: false,
    access: "",

    demoSeedId: DEMO_SEED_ID
  };
}

async function seedSongs(state) {
  const usersSnapshot = await db.collection("users")
    .where("demoSeedId", "==", DEMO_SEED_ID)
    .where("role", "==", "USER")
    .get();

  const userIds = usersSnapshot.docs.map((doc) => doc.id);

  if (userIds.length === 0) {
    throw new Error("No demo users found. Seed users first.");
  }

  for (let i = 0; i < songSamples.length; i++) {
    const sample = songSamples[i];
    const uploaderId = userIds[i % userIds.length];

    const songDoc = createSongDoc(sample, i, uploaderId);

    await db.collection("songs").doc(sample.id).set(songDoc, { merge: true });

    state.firestoreDocs.push(`songs/${sample.id}`);

    console.log(`Seeded song: ${sample.title} - ${sample.status}`);
  }
}

function createCommentDoc(songId, user, index, content, reportsCount = 0) {
  const commentId = `demo_comment_${songId}_${index}`;

  return {
    id: commentId,
    songId,
    userId: user.uid,
    displayName: user.displayName,
    avatarUrl: user.avatarUrl || "",
    content,
    reportsCount,
    isDeleted: false,
    deletedBy: "",
    deletedAt: 0,
    createdAt: now() - index * 600000,
    updatedAt: now(),
    demoSeedId: DEMO_SEED_ID
  };
}

async function seedComments(state) {
  const usersSnapshot = await db.collection("users")
    .where("demoSeedId", "==", DEMO_SEED_ID)
    .where("role", "==", "USER")
    .get();

  const users = usersSnapshot.docs.map((doc) => ({
    uid: doc.id,
    ...doc.data()
  }));

  const commentTemplates = [
    "Bài này nghe rất cuốn.",
    "Mix ổn, có thể thêm bass mạnh hơn.",
    "Giai điệu hay, phù hợp nghe khi học.",
    "Demo comment để test chức năng bình luận.",
    "Nội dung này cần admin kiểm tra."
  ];

  const targetSongIds = [
    "demo_song_01",
    "demo_song_02",
    "demo_song_03",
    "demo_song_06"
  ];

  for (const songId of targetSongIds) {
    let commentsCount = 0;

    for (let i = 0; i < 5; i++) {
      const user = users[(i + targetSongIds.indexOf(songId)) % users.length];
      const reportsCount = songId === "demo_song_06" && i === 4 ? 2 : 0;

      const comment = createCommentDoc(
        songId,
        user,
        i + 1,
        commentTemplates[i],
        reportsCount
      );

      await db.collection("songs")
        .doc(songId)
        .collection("comments")
        .doc(comment.id)
        .set(comment, { merge: true });

      state.firestoreDocs.push(`songs/${songId}/comments/${comment.id}`);

      commentsCount++;
    }

    await db.collection("songs").doc(songId).set(
      {
        commentsCount,
        updatedAt: now()
      },
      { merge: true }
    );

    console.log(`Seeded ${commentsCount} comments for song: ${songId}`);
  }
}

async function seedReports(state) {
  const usersSnapshot = await db.collection("users")
    .where("demoSeedId", "==", DEMO_SEED_ID)
    .where("role", "==", "USER")
    .get();

  const users = usersSnapshot.docs.map((doc) => ({
    uid: doc.id,
    ...doc.data()
  }));

  const reports = [
    {
      id: "demo_report_song_01",
      targetType: "song",
      targetId: "demo_song_02",
      reporterId: users[0].uid,
      reason: "spam",
      description: "Bài hát có dấu hiệu spam nội dung.",
      status: "PENDING"
    },
    {
      id: "demo_report_song_02",
      targetType: "song",
      targetId: "demo_song_06",
      reporterId: users[1].uid,
      reason: "offensive_content",
      description: "Nội dung có thể không phù hợp tiêu chuẩn cộng đồng.",
      status: "PENDING"
    },
    {
      id: "demo_report_song_03",
      targetType: "song",
      targetId: "demo_song_08",
      reporterId: users[2].uid,
      reason: "copyright",
      description: "Cần kiểm tra bản quyền trước khi duyệt.",
      status: "PENDING"
    },
    {
      id: "demo_report_comment_01",
      targetType: "comment",
      targetId: "demo_comment_demo_song_06_5",
      reporterId: users[3].uid,
      reason: "offensive_content",
      description: "demo_song_06|Bình luận có nội dung cần kiểm tra.",
      status: "PENDING"
    },
    {
      id: "demo_report_comment_02",
      targetType: "comment",
      targetId: "demo_comment_demo_song_06_5",
      reporterId: users[4].uid,
      reason: "spam",
      description: "demo_song_06|Bình luận bị báo cáo lần hai.",
      status: "PENDING"
    }
  ];

  for (const report of reports) {
    const reportDoc = {
      ...report,
      reviewedBy: "",
      reviewedAt: 0,
      adminNote: "",
      createdAt: now(),
      updatedAt: now(),
      demoSeedId: DEMO_SEED_ID
    };

    await db.collection("reports").doc(report.id).set(reportDoc, { merge: true });

    state.firestoreDocs.push(`reports/${report.id}`);

    console.log(`Seeded report: ${report.id}`);
  }
}

async function seedFollowsAndLikes(state) {
  const usersSnapshot = await db.collection("users")
    .where("demoSeedId", "==", DEMO_SEED_ID)
    .where("role", "==", "USER")
    .get();

  const users = usersSnapshot.docs.map((doc) => doc.id);

  const likedSongIds = ["demo_song_01", "demo_song_02", "demo_song_06"];

  for (let i = 0; i < Math.min(10, users.length); i++) {
    const userId = users[i];

    for (const songId of likedSongIds) {
      const likedPath = `users/${userId}/likedSongs/${songId}`;

      await db.doc(likedPath).set({
        songId,
        likedAt: now(),
        demoSeedId: DEMO_SEED_ID
      });

      state.firestoreDocs.push(likedPath);
    }

    const recentlyPath = `users/${userId}/recentlyPlayed/demo_song_01`;

    await db.doc(recentlyPath).set({
      songId: "demo_song_01",
      playedAt: now(),
      demoSeedId: DEMO_SEED_ID
    });

    state.firestoreDocs.push(recentlyPath);
  }

  console.log("Seeded likes and recently played data.");
}

async function seed() {
  const state = {
    seedId: DEMO_SEED_ID,
    createdAt: new Date().toISOString(),
    authUsers: [],
    firestoreDocs: []
  };

  console.log("Starting Orange Music demo seed...");

  await seedUsers(state);
  await seedSongs(state);
  await seedComments(state);
  await seedReports(state);
  await seedFollowsAndLikes(state);

  state.authUsers = [...new Set(state.authUsers)];
  state.firestoreDocs = [...new Set(state.firestoreDocs)];

  saveState(state);

  console.log("");
  console.log("Seed completed.");
  console.log("Demo accounts:");
  console.log("Admin: admin1@orangemusic.demo / Demo@123456");
  console.log("Admin: admin2@orangemusic.demo / Demo@123456");
  console.log("User : user01@orangemusic.demo / Demo@123456");
}

async function safeDeleteDocPath(docPath) {
  try {
    await db.doc(docPath).delete();
    console.log(`Deleted doc: ${docPath}`);
  } catch (error) {
    console.warn(`Could not delete doc ${docPath}: ${error.message}`);
  }
}

async function rollbackByState() {
  const state = loadState();

  const paths = [...new Set(state.firestoreDocs || [])]
    .sort((a, b) => b.split("/").length - a.split("/").length);

  for (const docPath of paths) {
    await safeDeleteDocPath(docPath);
  }

  for (const uid of [...new Set(state.authUsers || [])]) {
    try {
      const user = await auth.getUser(uid);

      if (user.email && user.email.endsWith("@orangemusic.demo")) {
        await auth.deleteUser(uid);
        console.log(`Deleted auth user: ${user.email}`);
      }
    } catch (error) {
      console.warn(`Could not delete auth user ${uid}: ${error.message}`);
    }
  }
}

async function rollbackByQuery() {
  const collections = ["reports", "songs", "users"];

  for (const collectionName of collections) {
    const snapshot = await db.collection(collectionName)
      .where("demoSeedId", "==", DEMO_SEED_ID)
      .get();

    for (const doc of snapshot.docs) {
      await doc.ref.delete();
      console.log(`Deleted ${collectionName}/${doc.id}`);
    }
  }

  const commentSnapshot = await db.collectionGroup("comments")
    .where("demoSeedId", "==", DEMO_SEED_ID)
    .get();

  for (const doc of commentSnapshot.docs) {
    await doc.ref.delete();
    console.log(`Deleted comment: ${doc.ref.path}`);
  }

  const authUsers = await auth.listUsers(1000);

  for (const user of authUsers.users) {
    if (user.email && user.email.endsWith("@orangemusic.demo")) {
      await auth.deleteUser(user.uid);
      console.log(`Deleted auth user: ${user.email}`);
    }
  }
}

async function rollback() {
  console.log("Rolling back Orange Music demo seed...");

  await rollbackByState();
  await rollbackByQuery();

  if (fs.existsSync(STATE_FILE)) {
    fs.unlinkSync(STATE_FILE);
  }

  console.log("Rollback completed.");
}

async function main() {
  const command = process.argv[2];

  if (command === "seed") {
    await seed();
    return;
  }

  if (command === "rollback") {
    await rollback();
    return;
  }

  console.log("Usage:");
  console.log("node seed-demo-data.js seed");
  console.log("node seed-demo-data.js rollback");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });