const fs = require("fs");
const path = require("path");
const { cert, initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const SERVICE_ACCOUNT_PATH =
  process.env.GOOGLE_APPLICATION_CREDENTIALS ||
  path.join(__dirname, "..", "serviceAccountKey.json");
const BATCH_LIMIT = 400;

if (!fs.existsSync(SERVICE_ACCOUNT_PATH)) {
  console.error("Missing service account key:", SERVICE_ACCOUNT_PATH);
  process.exit(1);
}

initializeApp({
  credential: cert(require(SERVICE_ACCOUNT_PATH))
});

const db = getFirestore();

async function run() {
  const snapshot = await db.collection("songs").get();
  let batch = db.batch();
  let batchSize = 0;
  let changed = 0;
  let normalizedStatuses = 0;
  let addedDeletedFlags = 0;

  for (const document of snapshot.docs) {
    const song = document.data();
    const updates = {};

    if (!Object.prototype.hasOwnProperty.call(song, "isDeleted")) {
      updates.isDeleted = false;
      addedDeletedFlags += 1;
    }

    if (song.status === "APPROVED") {
      updates.status = "approved";
      normalizedStatuses += 1;
    }

    if (Object.keys(updates).length === 0) continue;

    batch.set(document.ref, updates, { merge: true });
    batchSize += 1;
    changed += 1;

    if (batchSize === BATCH_LIMIT) {
      await batch.commit();
      batch = db.batch();
      batchSize = 0;
    }
  }

  if (batchSize > 0) await batch.commit();

  console.log("Catalog migration complete", {
    scanned: snapshot.size,
    changed,
    normalizedStatuses,
    addedDeletedFlags
  });
}

run().catch((error) => {
  console.error("Catalog migration failed", error);
  process.exit(1);
});
