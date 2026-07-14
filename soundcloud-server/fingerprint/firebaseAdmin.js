const fs = require("fs");
const path = require("path");

const { applicationDefault, cert, getApps, initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

function resolveServiceAccountPath() {
  const configuredPath =
    process.env.FIREBASE_SERVICE_ACCOUNT_PATH ||
    process.env.GOOGLE_APPLICATION_CREDENTIALS ||
    "";

  if (!configuredPath) {
    return path.join(__dirname, "..", "serviceAccountKey.json");
  }

  return path.isAbsolute(configuredPath)
    ? configuredPath
    : path.resolve(__dirname, "..", configuredPath);
}

function initializeFirebaseAdmin() {
  if (getApps().length > 0) {
    return;
  }

  const serviceAccountPath = resolveServiceAccountPath();

  if (fs.existsSync(serviceAccountPath)) {
    initializeApp({
      credential: cert(require(serviceAccountPath))
    });
    return;
  }

  initializeApp({
    credential: applicationDefault()
  });
}

function getDb() {
  initializeFirebaseAdmin();
  return getFirestore();
}

module.exports = {
  getDb,
  resolveServiceAccountPath
};
