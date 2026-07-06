const path = require("path");

const axios = require("axios");
const { cert, initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const SERVICE_ACCOUNT_PATH =
  process.env.GOOGLE_APPLICATION_CREDENTIALS ||
  path.join(__dirname, "..", "serviceAccountKey.json");

const DEFAULT_API_BASE_URL =
  process.env.SOUNDCLOUD_API_BASE_URL || "http://127.0.0.1:3000";

const DEFAULT_PUBLIC_BASE_URL =
  process.env.SOUNDCLOUD_PUBLIC_BASE_URL ||
  process.env.SONGURL_PUBLIC_BASE_URL ||
  DEFAULT_API_BASE_URL;

const DEFAULT_LIMIT = 20;
const DEFAULT_DELAY_MS = 500;

function parseArgs(argv) {
  const args = {
    write: false,
    limit: DEFAULT_LIMIT,
    delayMs: DEFAULT_DELAY_MS,
    apiBaseUrl: DEFAULT_API_BASE_URL,
    publicBaseUrl: DEFAULT_PUBLIC_BASE_URL,
    onlyId: ""
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    const next = argv[index + 1];

    if (arg === "--write") args.write = true;
    if (arg === "--dry-run") args.write = false;
    if (arg === "--limit" && next) {
      args.limit = Number(next);
      index += 1;
    }
    if (arg === "--delay-ms" && next) {
      args.delayMs = Number(next);
      index += 1;
    }
    if (arg === "--api-base-url" && next) {
      args.apiBaseUrl = next;
      index += 1;
    }
    if (arg === "--public-base-url" && next) {
      args.publicBaseUrl = next;
      index += 1;
    }
    if (arg === "--only-id" && next) {
      args.onlyId = next;
      index += 1;
    }
  }

  return args;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function trimTrailingSlash(value) {
  return String(value || "").replace(/\/+$/, "");
}

function normalizeTrackId(songId, song) {
  const soundCloudId = Number(song.soundCloudId || 0);
  if (soundCloudId > 0) return String(soundCloudId);

  return String(songId || "")
    .replace(/^soundcloud_/i, "")
    .trim();
}

function isSoundCloudCandidate(songId, song) {
  const source = String(song.source || "").toLowerCase();
  const hasSoundCloudId = Number(song.soundCloudId || 0) > 0;

  return (
    String(song.songUrl || "").trim() === "" &&
    song.isDeleted !== true &&
    (
      source.includes("soundcloud") ||
      String(songId || "").startsWith("soundcloud_") ||
      hasSoundCloudId
    )
  );
}

function rewriteProxyUrlOrigin(streamUrl, publicBaseUrl) {
  if (!streamUrl) return "";

  try {
    const parsedStreamUrl = new URL(streamUrl);

    if (parsedStreamUrl.pathname !== "/soundcloud/proxy/media") {
      return streamUrl;
    }

    const publicBase = new URL(trimTrailingSlash(publicBaseUrl));
    publicBase.pathname = parsedStreamUrl.pathname;
    publicBase.search = parsedStreamUrl.search;
    publicBase.hash = "";

    return publicBase.toString();
  } catch (_) {
    return streamUrl;
  }
}

function previewUrl(value) {
  const text = String(value || "");
  return text.length > 180 ? `${text.slice(0, 180)}...` : text;
}

async function resolveStreamUrl(apiBaseUrl, trackId) {
  const response = await axios.get(`${trimTrailingSlash(apiBaseUrl)}/getStreamUrl`, {
    params: { trackId },
    timeout: 45_000
  });

  return response.data?.streamUrl || "";
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  if (!require("fs").existsSync(SERVICE_ACCOUNT_PATH)) {
    throw new Error(`Missing service account key: ${SERVICE_ACCOUNT_PATH}`);
  }

  if (!Number.isFinite(args.limit) || args.limit <= 0) {
    throw new Error("--limit must be a positive number");
  }

  initializeApp({
    credential: cert(require(SERVICE_ACCOUNT_PATH))
  });

  const db = getFirestore();
  const snapshot = await db.collection("songs").get();

  const candidates = snapshot.docs
    .filter((document) => {
      if (args.onlyId && document.id !== args.onlyId) return false;
      return isSoundCloudCandidate(document.id, document.data() || {});
    })
    .slice(0, args.limit);

  console.log(
    JSON.stringify(
      {
        mode: args.write ? "write" : "dry-run",
        apiBaseUrl: args.apiBaseUrl,
        publicBaseUrl: args.publicBaseUrl,
        totalSongs: snapshot.size,
        candidates: candidates.length,
        limit: args.limit
      },
      null,
      2
    )
  );

  if (!args.write) {
    console.log("Dry-run only. Add --write to update Firestore.");
  }

  let updated = 0;
  let failed = 0;
  let skipped = 0;

  for (const document of candidates) {
    const song = document.data() || {};
    const trackId = normalizeTrackId(document.id, song);

    if (!trackId || trackId === document.id) {
      skipped += 1;
      console.log(`[skip] ${document.id}: cannot resolve SoundCloud track id`);
      continue;
    }

    try {
      const rawStreamUrl = await resolveStreamUrl(args.apiBaseUrl, trackId);
      const songUrl = rewriteProxyUrlOrigin(rawStreamUrl, args.publicBaseUrl);

      if (!songUrl) {
        skipped += 1;
        console.log(`[skip] ${document.id}: empty streamUrl`);
        continue;
      }

      if (args.write) {
        await document.ref.set(
          {
            songUrl,
            streamUrlSource: "soundcloud_api_proxy",
            streamResolvedAt: Date.now(),
            updatedAt: Date.now()
          },
          { merge: true }
        );
      }

      updated += 1;
      console.log(`[${args.write ? "updated" : "resolved"}] ${document.id}: ${previewUrl(songUrl)}`);
    } catch (error) {
      failed += 1;
      const status = error.response?.status ? ` status=${error.response.status}` : "";
      const message = error.response?.data?.message || error.message;
      console.log(`[fail] ${document.id}:${status} ${message}`);
    }

    if (args.delayMs > 0) {
      await sleep(args.delayMs);
    }
  }

  console.log(
    JSON.stringify(
      {
        mode: args.write ? "write" : "dry-run",
        updated,
        skipped,
        failed
      },
      null,
      2
    )
  );
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
