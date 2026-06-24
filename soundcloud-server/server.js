require("dotenv").config();

const express = require("express");
const axios = require("axios");
const cors = require("cors");
const fs = require("fs");
const path = require("path");

const app = express();

app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;
const SOUNDCLOUD_CLIENT_ID = process.env.SOUNDCLOUD_CLIENT_ID;
const SOUNDCLOUD_CLIENT_SECRET = process.env.SOUNDCLOUD_CLIENT_SECRET;

const TOKEN_CACHE_FILE = path.join(__dirname, ".soundcloud-token-cache.json");
const ORANGE_STORE_FILE = path.join(__dirname, "data", "orange-music-store.json");

const SEARCH_CACHE_TTL_MS = 2 * 60 * 1000;
// SoundCloud CDN URLs are signed and can expire quickly. Cache the local
// proxy URL for a short period instead of exposing a signed CDN URL to Android.
const STREAM_CACHE_TTL_MS = 2 * 60 * 1000;

let cachedToken = null;
let cachedTokenExpiresAt = 0;

const searchCache = new Map();
const streamCache = new Map();

function checkConfig() {
  if (!SOUNDCLOUD_CLIENT_ID || !SOUNDCLOUD_CLIENT_SECRET) {
    throw new Error("Missing SOUNDCLOUD_CLIENT_ID or SOUNDCLOUD_CLIENT_SECRET in .env");
  }
}

function normalizeLimit(value) {
  const limit = Number(value || 10);

  if (Number.isNaN(limit)) return 10;
  if (limit < 1) return 1;
  if (limit > 20) return 20;

  return limit;
}

function normalizeSurfaceLimit(value) {
  const limit = Number(value || 20);

  if (Number.isNaN(limit)) return 20;
  if (limit < 1) return 1;
  if (limit > 50) return 50;

  return limit;
}

function normalizeTrackId(rawValue) {
  return String(rawValue || "")
    .trim()
    .replace("soundcloud_", "");
}

function normalizeText(value) {
  return String(value || "")
    .trim()
    .toLowerCase();
}

function isSameArtist(track, artistName) {
  const trackArtist = normalizeText(track.artist);
  const targetArtist = normalizeText(artistName);

  if (!trackArtist || !targetArtist) {
    return false;
  }

  return (
    trackArtist === targetArtist ||
    trackArtist.includes(targetArtist) ||
    targetArtist.includes(trackArtist)
  );
}

function isRateLimitError(error) {
  return error && error.response && error.response.status === 429;
}

function logSoundCloudError(label, error) {
  console.error("===== SOUNDCLOUD DEBUG =====");
  console.error("Label:", label);
  console.error("Status:", error.response?.status);
  console.error("Method:", error.config?.method);
  console.error("URL:", error.config?.url);
  console.error("Params:", error.config?.params);
  console.error("Retry-After:", error.response?.headers?.["retry-after"]);
  console.error("Response:", error.response?.data || error.message);
  console.error("============================");
}

function buildErrorResponse(message, error) {
  return {
    message: message,
    source: error.config?.url || "",
    method: error.config?.method || "",
    params: error.config?.params || null,
    retryAfter: error.response?.headers?.["retry-after"] || "",
    detail: error.response?.data || error.message
  };
}

function readTokenCacheFromDisk() {
  try {
    if (!fs.existsSync(TOKEN_CACHE_FILE)) return null;

    const raw = fs.readFileSync(TOKEN_CACHE_FILE, "utf8");
    const data = JSON.parse(raw);

    if (!data.accessToken || !data.expiresAt) return null;

    return data;
  } catch (error) {
    console.log("Read token cache failed:", error.message);
    return null;
  }
}

function saveTokenCacheToDisk(accessToken, expiresAt) {
  try {
    fs.writeFileSync(
      TOKEN_CACHE_FILE,
      JSON.stringify(
        {
          accessToken: accessToken,
          expiresAt: expiresAt
        },
        null,
        2
      )
    );
  } catch (error) {
    console.log("Save token cache failed:", error.message);
  }
}

function getFromCache(cacheMap, key) {
  const cached = cacheMap.get(key);

  if (!cached) return null;

  if (Date.now() > cached.expiresAt) {
    cacheMap.delete(key);
    return null;
  }

  return cached.data;
}

function setToCache(cacheMap, key, data, ttlMs) {
  cacheMap.set(key, {
    data: data,
    expiresAt: Date.now() + ttlMs
  });
}

function getBaseUrlFromRequest(req) {
  return `${req.protocol}://${req.get("host")}`;
}

function buildProxyUrl(req, pathName, targetUrl) {
  return `${getBaseUrlFromRequest(req)}${pathName}?url=${encodeURIComponent(targetUrl)}`;
}

function shouldSendSoundCloudAuth(targetUrl) {
  try {
    const host = new URL(targetUrl).hostname;
    return host.includes("soundcloud.com");
  } catch (error) {
    return false;
  }
}

function toAbsoluteUrl(baseUrl, maybeRelativeUrl) {
  try {
    return new URL(maybeRelativeUrl, baseUrl).toString();
  } catch (error) {
    return maybeRelativeUrl;
  }
}

function isHlsUrl(url) {
  if (!url) return false;

  return (
    url.includes(".m3u8") ||
    url.includes("/hls") ||
    url.includes("/soundcloud/proxy/hls")
  );
}

function getProtocolFromUrl(url) {
  if (!url) return "";

  if (isHlsUrl(url)) {
    return "hls";
  }

  return "progressive";
}

function getMimeTypeFromUrl(url) {
  if (!url) return "";

  if (isHlsUrl(url)) {
    return "application/x-mpegURL";
  }

  return "audio/mpeg";
}

async function getSoundCloudAccessToken() {
  checkConfig();

  const now = Date.now();

  if (cachedToken && now < cachedTokenExpiresAt) {
    return cachedToken;
  }

  const diskCache = readTokenCacheFromDisk();

  if (diskCache && diskCache.accessToken && now < diskCache.expiresAt) {
    cachedToken = diskCache.accessToken;
    cachedTokenExpiresAt = diskCache.expiresAt;
    console.log("Use cached SoundCloud token from disk");
    return cachedToken;
  }

  console.log("Request new SoundCloud access token");

  const body = new URLSearchParams();
  body.append("grant_type", "client_credentials");

  const response = await axios.post(
    "https://secure.soundcloud.com/oauth/token",
    body,
    {
      auth: {
        username: SOUNDCLOUD_CLIENT_ID,
        password: SOUNDCLOUD_CLIENT_SECRET
      },
      headers: {
        "Content-Type": "application/x-www-form-urlencoded"
      }
    }
  );

  cachedToken = response.data.access_token;

  const expiresInSeconds = response.data.expires_in || 3600;
  const safeExpiresInSeconds = Math.max(expiresInSeconds - 120, 60);

  cachedTokenExpiresAt = now + safeExpiresInSeconds * 1000;

  saveTokenCacheToDisk(cachedToken, cachedTokenExpiresAt);

  return cachedToken;
}

function getSoundCloudHeaders(accessToken) {
  return {
    Authorization: `OAuth ${accessToken}`
  };
}

function chooseBestTranscoding(transcodings) {
  if (!Array.isArray(transcodings) || transcodings.length === 0) {
    return null;
  }

  const progressiveMp3 = transcodings.find((item) => {
    return (
      item.format &&
      item.format.protocol === "progressive" &&
      item.format.mime_type === "audio/mpeg"
    );
  });

  return progressiveMp3 || null;
}

function chooseBestStreamFromStreamsEndpoint(streams) {
  if (!streams) return "";

  return streams.http_mp3_128_url || "";
}

async function resolveProgressiveStreamUrl(req, streamApiUrl, accessToken) {
  if (!streamApiUrl) return "";

  if (isHlsUrl(streamApiUrl)) {
    console.log("Skip HLS URL:", streamApiUrl);
    return "";
  }

  try {
    const response = await axios.get(streamApiUrl, {
      headers: getSoundCloudHeaders(accessToken),
      responseType: "stream",
      maxRedirects: 0,
      timeout: 15000,
      validateStatus: (status) => {
        return status >= 200 && status < 400;
      }
    });

    const location = response.headers.location;

    if (location) {
      const finalUrl = toAbsoluteUrl(streamApiUrl, location);

      console.log("Resolved CDN URL:", finalUrl);

      if (response.data && response.data.destroy) {
        response.data.destroy();
      }

      // Keep the SoundCloud API URL behind the local proxy. Each proxy request
      // follows a fresh redirect to the signed CDN URL and adds OAuth when it
      // is required. Returning the CDN URL directly causes ExoPlayer 403s when
      // that signature expires or the CDN rejects the Android request.
      return buildProxyUrl(req, "/soundcloud/proxy/media", streamApiUrl);
    }

    const contentType = response.headers["content-type"] || "";

    if (contentType.includes("audio")) {
      console.log("SoundCloud returned audio directly, use proxy media fallback");

      if (response.data && response.data.destroy) {
        response.data.destroy();
      }

      return buildProxyUrl(req, "/soundcloud/proxy/media", streamApiUrl);
    }

    if (response.data && response.data.destroy) {
      response.data.destroy();
    }

    console.log("Cannot resolve progressive URL, content-type:", contentType);
    return "";
  } catch (error) {
    if (error.response?.status === 302 || error.response?.status === 301) {
      const location = error.response.headers.location;

      if (location) {
        const finalUrl = toAbsoluteUrl(streamApiUrl, location);
        console.log("Resolved CDN URL from redirect error:", finalUrl);
        return buildProxyUrl(req, "/soundcloud/proxy/media", streamApiUrl);
      }
    }

    logSoundCloudError("resolveProgressiveStreamUrl", error);

    return buildProxyUrl(req, "/soundcloud/proxy/media", streamApiUrl);
  }
}

async function getTrackDetail(trackId, accessToken) {
  const response = await axios.get(
    `https://api.soundcloud.com/tracks/${trackId}`,
    {
      headers: getSoundCloudHeaders(accessToken)
    }
  );

  return response.data;
}

async function getStreamFromTranscoding(req, selectedTranscoding, accessToken) {
  if (!selectedTranscoding || !selectedTranscoding.url) {
    return "";
  }

  const response = await axios.get(selectedTranscoding.url, {
    headers: getSoundCloudHeaders(accessToken),
    timeout: 15000
  });

  const streamApiUrl =
    response.data && response.data.url
      ? response.data.url
      : "";

  if (!streamApiUrl) {
    return "";
  }

  console.log("Progressive API URL from transcoding:", streamApiUrl);

  return await resolveProgressiveStreamUrl(req, streamApiUrl, accessToken);
}

async function getStreamFromStreamsEndpoint(req, trackId, accessToken) {
  const streamsResponse = await axios.get(
    `https://api.soundcloud.com/tracks/${trackId}/streams`,
    {
      headers: getSoundCloudHeaders(accessToken),
      timeout: 15000
    }
  );

  const streamApiUrl = chooseBestStreamFromStreamsEndpoint(streamsResponse.data);

  if (!streamApiUrl) {
    console.log("No progressive stream API URL found");
    return "";
  }

  console.log("Progressive Stream API URL:", streamApiUrl);

  return await resolveProgressiveStreamUrl(req, streamApiUrl, accessToken);
}

function mapTrackToAndroid(track) {
  const soundCloudUser = track.user || {};

  return {
    id: `soundcloud_${track.id}`,
    soundCloudId: track.id || 0,

    title: track.title || "",
    artist: soundCloudUser.username || "",
    coverUrl: track.artwork_url || soundCloudUser.avatar_url || "",

    duration: Math.floor((track.duration || 0) / 1000),
    genre: track.genre || "",
    permalinkUrl: track.permalink_url || "",

    playbackCount: track.playback_count || 0,
    likesCount: track.likes_count || 0,
    commentsCount: track.comment_count || 0,

    plays: track.playback_count || 0,
    likes: track.likes_count || 0,

    streamable: track.streamable === true,
    access: track.access || "",

    source: "soundcloud",
    sourceLabel: "SoundCloud",

    uploaderId: soundCloudUser.id
      ? `soundcloud_user_${soundCloudUser.id}`
      : "soundcloud",

    uploaderName: soundCloudUser.username || "",
    uploaderUsername: soundCloudUser.username || "",
    uploaderAvatarUrl: soundCloudUser.avatar_url || "",
    uploaderPermalinkUrl: soundCloudUser.permalink_url || ""
  };
}

async function searchSoundCloudTracksInternal(query, limit = 20) {
  const finalQuery = String(query || "").trim();
  const finalLimit = normalizeSurfaceLimit(limit);

  if (!finalQuery) {
    return [];
  }

  const cacheKey = `${finalQuery.toLowerCase()}_${finalLimit}`;
  const cachedResult = getFromCache(searchCache, cacheKey);

  if (cachedResult && Array.isArray(cachedResult.results)) {
    return cachedResult.results;
  }

  const accessToken = await getSoundCloudAccessToken();

  const response = await axios.get("https://api.soundcloud.com/tracks", {
    headers: getSoundCloudHeaders(accessToken),
    params: {
      q: finalQuery,
      limit: finalLimit,
      access: "playable"
    }
  });

  const tracks = Array.isArray(response.data) ? response.data : [];

  const results = tracks
    .filter((track) => track.id)
    .filter((track) => track.title)
    .filter((track) => {
      if (track.access) {
        return track.access === "playable";
      }

      return track.streamable === true;
    })
    .map(mapTrackToAndroid);

  setToCache(
    searchCache,
    cacheKey,
    {
      results: results
    },
    SEARCH_CACHE_TTL_MS
  );

  return results;
}

function createApiArtistProfile(artistName, tracks) {
  const firstTrack = tracks[0] || {};

  return {
    id: `soundcloud_artist_${encodeURIComponent(artistName)}`,
    artistName: artistName,
    displayName: artistName,
    username: artistName,
    source: "soundcloud",
    sourceLabel: "SoundCloud",
    avatarUrl: firstTrack.uploaderAvatarUrl || firstTrack.coverUrl || "",
    bannerUrl: firstTrack.coverUrl || "",
    bio: "Artist profile generated from available SoundCloud track data.",
    followersCount: 0,
    followingCount: 0,
    tracksCount: tracks.length,
    playlistsCount: 0,
    topTracks: tracks
  };
}

function createApiPlaylist(query, tracks) {
  const firstTrack = tracks[0] || {};

  return {
    id: `soundcloud_playlist_${encodeURIComponent(query)}`,
    name: query || "SoundCloud Playlist",
    description: "Playlist generated from available SoundCloud search results.",
    source: "soundcloud",
    sourceLabel: "SoundCloud",
    coverUrl: firstTrack.coverUrl || "",
    ownerId: "soundcloud",
    ownerName: "SoundCloud",
    isPublic: true,
    songsCount: tracks.length,
    tracks: tracks
  };
}

function createEmptyOrangeStore() {
  return {
    users: {},
    playlists: {},
    commentsByTrack: {},
    likesByTrack: {}
  };
}

function readOrangeStore() {
  try {
    if (!fs.existsSync(ORANGE_STORE_FILE)) {
      return createEmptyOrangeStore();
    }

    const raw = fs.readFileSync(ORANGE_STORE_FILE, "utf8");
    const data = JSON.parse(raw);

    return {
      users: data.users || {},
      playlists: data.playlists || {},
      commentsByTrack: data.commentsByTrack || {},
      likesByTrack: data.likesByTrack || {}
    };
  } catch (error) {
    console.log("Read orange store failed:", error.message);
    return createEmptyOrangeStore();
  }
}

function saveOrangeStore(store) {
  try {
    const dir = path.dirname(ORANGE_STORE_FILE);

    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, {
        recursive: true
      });
    }

    fs.writeFileSync(
      ORANGE_STORE_FILE,
      JSON.stringify(store, null, 2)
    );
  } catch (error) {
    console.log("Save orange store failed:", error.message);
  }
}

function createId(prefix) {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

function getSafeUserId(value) {
  return String(value || "").trim() || "guest";
}

function getTrackKey(trackId) {
  return normalizeTrackId(trackId);
}

function getTrackDocumentId(trackKey) {
  return trackKey.startsWith("soundcloud_")
    ? trackKey
    : `soundcloud_${trackKey}`;
}

function normalizeStoredTrack(track) {
  if (!track) return null;

  const trackId = track.id || track.soundCloudId || "";

  return {
    ...track,
    id: String(trackId).startsWith("soundcloud_")
      ? String(trackId)
      : `soundcloud_${trackId}`,
    source: track.source || "soundcloud",
    sourceLabel: track.sourceLabel || "SoundCloud"
  };
}

function getPublicUser(userId, fallback = {}) {
  return {
    userId: userId,
    displayName: fallback.displayName || fallback.username || "Orange Music User",
    username: fallback.username || "",
    avatarUrl: fallback.avatarUrl || "",
    bio: fallback.bio || "",
    source: "orange_music"
  };
}

app.get("/", (req, res) => {
  res.json({
    message: "Orange Music streaming proxy is running",
    mode: "progressive-only",
    endpoints: [
      "/health",
      "/searchSoundCloudTracks?q=lofi&limit=10",
      "/getStreamUrl?trackId=123456789",
      "/debug/buffering-test?trackId=123456789&bytes=1048576",

      "/getSoundCloudArtistProfile?artist=Alan%20Walker&limit=20",
      "/getSoundCloudArtistTracks?artist=Alan%20Walker&limit=20",
      "/getSoundCloudApiPlaylist?q=chill&limit=30",

      "/upsertOrangeMusicUser",
      "/getOrangeMusicUser?userId=demo_user",

      "/getUserApiPlaylists?userId=demo_user",
      "/createUserApiPlaylist",
      "/addTrackToUserApiPlaylist",
      "/removeTrackFromUserApiPlaylist",

      "/getSoundCloudTrackComments?trackId=soundcloud_123456789",
      "/addSoundCloudTrackComment",
      "/deleteSoundCloudTrackComment",

      "/getSoundCloudTrackSocial?trackId=soundcloud_123456789&userId=demo_user",
      "/toggleSoundCloudTrackLike"
    ]
  });
});

app.get("/health", (req, res) => {
  res.json({
    status: "ok",
    mode: "progressive-only",
    port: PORT,
    hasClientId: Boolean(SOUNDCLOUD_CLIENT_ID),
    hasClientSecret: Boolean(SOUNDCLOUD_CLIENT_SECRET),
    hasMemoryToken: Boolean(cachedToken),
    tokenExpiresAt: cachedTokenExpiresAt || null,
    searchCacheSize: searchCache.size,
    streamCacheSize: streamCache.size
  });
});

app.get("/debug/buffering-test", async (req, res) => {
  const startedAt = Date.now();

  try {
    const trackId = normalizeTrackId(req.query.trackId);
    const bytes = Math.max(
      64 * 1024,
      Math.min(Number(req.query.bytes || 1024 * 1024), 5 * 1024 * 1024)
    );

    if (!trackId) {
      return res.status(400).json({
        message: "Missing trackId",
        example: "/debug/buffering-test?trackId=2176251972&bytes=1048576"
      });
    }

    const baseUrl = getBaseUrlFromRequest(req);

    const resolveStart = Date.now();

    const streamResponse = await axios.get(
      `${baseUrl}/getStreamUrl`,
      {
        params: {
          trackId: trackId
        },
        timeout: 20000
      }
    );

    const resolveStreamMs = Date.now() - resolveStart;
    const streamUrl = streamResponse.data?.streamUrl || "";

    if (!streamUrl) {
      return res.status(404).json({
        message: "Stream URL not found",
        resolveStreamMs: resolveStreamMs,
        streamResponse: streamResponse.data
      });
    }

    const headers = {
      Range: `bytes=0-${bytes - 1}`
    };

    if (shouldSendSoundCloudAuth(streamUrl)) {
      const accessToken = await getSoundCloudAccessToken();
      headers.Authorization = `OAuth ${accessToken}`;
    }

    const audioStart = Date.now();

    const audioResponse = await axios.get(streamUrl, {
      headers: headers,
      responseType: "stream",
      timeout: 30000,
      validateStatus: (status) => {
        return (status >= 200 && status < 300) || status === 206;
      }
    });

    const audioHeaderMs = Date.now() - audioStart;

    let firstByteMs = 0;
    let totalBytes = 0;
    let hasFirstByte = false;

    audioResponse.data.on("data", (chunk) => {
      if (!hasFirstByte) {
        hasFirstByte = true;
        firstByteMs = Date.now() - startedAt;
      }

      totalBytes += chunk.length;
    });

    audioResponse.data.on("end", () => {
      const totalMs = Date.now() - startedAt;
      const audioDownloadMs = Date.now() - audioStart;
      const seconds = audioDownloadMs / 1000;

      const mbps = seconds > 0
        ? Number(((totalBytes * 8) / seconds / 1000 / 1000).toFixed(2))
        : 0;

      const verdict =
        firstByteMs <= 800 && mbps >= 1.5
          ? "good"
          : firstByteMs <= 1500 && mbps >= 0.8
            ? "acceptable"
            : "slow";

      return res.json({
        trackId: trackId,
        requestedBytes: bytes,
        receivedBytes: totalBytes,

        stream: {
          protocol: streamResponse.data?.protocol || "",
          mimeType: streamResponse.data?.mimeType || "",
          isProxyUrl: streamUrl.includes("/soundcloud/proxy/media")
        },

        timing: {
          resolveStreamMs: resolveStreamMs,
          audioHeaderMs: audioHeaderMs,
          firstByteMs: firstByteMs,
          audioDownloadMs: audioDownloadMs,
          totalMs: totalMs
        },

        speed: {
          mbps: mbps
        },

        responseHeaders: {
          status: audioResponse.status,
          contentType: audioResponse.headers["content-type"] || "",
          contentLength: audioResponse.headers["content-length"] || "",
          contentRange: audioResponse.headers["content-range"] || "",
          acceptRanges: audioResponse.headers["accept-ranges"] || ""
        },

        verdict: verdict
      });
    });

    audioResponse.data.on("error", (streamError) => {
      return res.status(500).json({
        message: "Buffering test stream failed",
        detail: streamError.message
      });
    });
  } catch (error) {
    logSoundCloudError("debug/buffering-test", error);

    return res.status(error.response?.status || 500).json({
      message: "Buffering test failed",
      detail: error.message,
      status: error.response?.status || null,
      data: error.response?.data || null
    });
  }
});

app.get("/searchSoundCloudTracks", async (req, res) => {
  try {
    const query = String(req.query.q || "").trim();
    const limit = normalizeLimit(req.query.limit);

    if (!query) {
      return res.status(400).json({
        message: "Missing query"
      });
    }

    const results = await searchSoundCloudTracksInternal(query, limit);

    return res.status(200).json({
      results: results
    });
  } catch (error) {
    logSoundCloudError("searchSoundCloudTracks", error);

    if (isRateLimitError(error)) {
      return res.status(429).json(
        buildErrorResponse(
          "SoundCloud rate limited at search endpoint.",
          error
        )
      );
    }

    return res.status(500).json(
      buildErrorResponse(
        "SoundCloud search failed.",
        error
      )
    );
  }
});

app.get("/soundcloud/proxy/media", async (req, res) => {
  try {
    const targetUrl = String(req.query.url || "").trim();

    if (!targetUrl) {
      return res.status(400).send("Missing url");
    }

    const accessToken = await getSoundCloudAccessToken();

    const headers = {};

    if (shouldSendSoundCloudAuth(targetUrl)) {
      headers.Authorization = `OAuth ${accessToken}`;
    }

    if (req.headers.range) {
      headers.Range = req.headers.range;
    }

    const response = await axios.get(targetUrl, {
      headers: headers,
      responseType: "stream",
      timeout: 20000,
      validateStatus: (status) => {
        return (status >= 200 && status < 300) || status === 206;
      }
    });

    if (response.headers["content-type"]) {
      res.setHeader("Content-Type", response.headers["content-type"]);
    } else {
      res.setHeader("Content-Type", "audio/mpeg");
    }

    if (response.headers["content-length"]) {
      res.setHeader("Content-Length", response.headers["content-length"]);
    }

    if (response.headers["content-range"]) {
      res.setHeader("Content-Range", response.headers["content-range"]);
    }

    if (response.headers["accept-ranges"]) {
      res.setHeader("Accept-Ranges", response.headers["accept-ranges"]);
    } else {
      res.setHeader("Accept-Ranges", "bytes");
    }

    res.setHeader("Cache-Control", "public, max-age=300");
    res.setHeader("Access-Control-Allow-Origin", "*");

    res.status(response.status);

    response.data.on("error", (streamError) => {
      console.error("Proxy media stream error:", streamError.message);

      if (!res.headersSent) {
        res.status(500).end();
      } else {
        res.end();
      }
    });

    return response.data.pipe(res);
  } catch (error) {
    logSoundCloudError("soundcloud/proxy/media", error);

    return res.status(error.response?.status || 500).send(
      error.response?.data || error.message
    );
  }
});

async function resolveLegacyStreamUrl(req, res) {
  try {
    const trackId = normalizeTrackId(req.query.trackId);

    if (!trackId) {
      return res.status(400).json({
        message: "Missing trackId"
      });
    }

    const cachedStream = getFromCache(streamCache, trackId);

    if (cachedStream) {
      console.log(`Return cached stream for track ${trackId}`);
      return res.status(200).json(cachedStream);
    }

    const accessToken = await getSoundCloudAccessToken();

    const track = await getTrackDetail(trackId, accessToken);

    if (!track) {
      return res.status(404).json({
        message: "Track not found"
      });
    }

    if (track.access && track.access !== "playable") {
      return res.status(403).json({
        message: "Track is not playable",
        access: track.access
      });
    }

    if (track.streamable !== true) {
      return res.status(403).json({
        message: "Track is not streamable",
        streamable: track.streamable
      });
    }

    let streamUrl = "";
    let protocol = "";
    let mimeType = "";

    const transcodings =
      track.media && Array.isArray(track.media.transcodings)
        ? track.media.transcodings
        : [];

    const selectedTranscoding = chooseBestTranscoding(transcodings);

    if (selectedTranscoding && selectedTranscoding.url) {
      try {
        streamUrl = await getStreamFromTranscoding(
          req,
          selectedTranscoding,
          accessToken
        );

        protocol = getProtocolFromUrl(streamUrl);
        mimeType = getMimeTypeFromUrl(streamUrl);

        if (streamUrl && protocol === "progressive") {
          console.log(`Progressive transcoding selected: ${protocol} - ${mimeType}`);
          console.log("Resolved stream URL:", streamUrl);
        }
      } catch (transcodingError) {
        logSoundCloudError("transcodingUrl", transcodingError);

        if (isRateLimitError(transcodingError)) {
          return res.status(429).json(
            buildErrorResponse(
              "SoundCloud rate limited at transcoding stream endpoint.",
              transcodingError
            )
          );
        }

        console.log("Progressive transcoding failed, fallback to streams endpoint");
      }
    }

    if (!streamUrl) {
      try {
        streamUrl = await getStreamFromStreamsEndpoint(req, trackId, accessToken);

        protocol = getProtocolFromUrl(streamUrl);
        mimeType = getMimeTypeFromUrl(streamUrl);

        if (streamUrl && protocol === "progressive") {
          console.log(`Progressive streams endpoint selected: ${protocol} - ${mimeType}`);
          console.log("Resolved stream URL:", streamUrl);
        }
      } catch (streamsError) {
        logSoundCloudError("tracks/:id/streams", streamsError);

        if (isRateLimitError(streamsError)) {
          return res.status(429).json(
            buildErrorResponse(
              "SoundCloud rate limited at streams endpoint.",
              streamsError
            )
          );
        }

        return res.status(500).json(
          buildErrorResponse(
            "SoundCloud streams endpoint failed.",
            streamsError
          )
        );
      }
    }

    if (!streamUrl || isHlsUrl(streamUrl)) {
      return res.status(404).json({
        message: "Progressive stream URL not found. This track may only support HLS."
      });
    }

    const result = {
      streamUrl: streamUrl,
      protocol: "progressive",
      mimeType: "audio/mpeg",
      duration: Math.floor((track.duration || 0) / 1000),
      access: track.access || "",
      streamable: track.streamable === true
    };

    setToCache(streamCache, trackId, result, STREAM_CACHE_TTL_MS);

    return res.status(200).json(result);
  } catch (error) {
    logSoundCloudError("getStreamUrl", error);

    if (isRateLimitError(error)) {
      return res.status(429).json(
        buildErrorResponse(
          "SoundCloud rate limited.",
          error
        )
      );
    }

    return res.status(500).json(
      buildErrorResponse(
        "SoundCloud stream failed.",
        error
      )
    );
  }
}

// New Android builds use this provider-neutral endpoint. Keep the old route so
// installed development APKs continue working while the app is updated.
app.get("/getStreamUrl", resolveLegacyStreamUrl);
app.get("/getSoundCloudStreamUrl", resolveLegacyStreamUrl);

app.get("/getSoundCloudArtistProfile", async (req, res) => {
  try {
    const artist = String(req.query.artist || "").trim();
    const limit = normalizeSurfaceLimit(req.query.limit);

    if (!artist) {
      return res.status(400).json({
        message: "Missing artist query parameter"
      });
    }

    const tracks = await searchSoundCloudTracksInternal(artist, limit);

    const artistTracks = tracks.filter((track) => {
      return isSameArtist(track, artist);
    });

    const finalTracks = artistTracks.length > 0 ? artistTracks : tracks;

    return res.status(200).json({
      profile: createApiArtistProfile(artist, finalTracks),
      results: finalTracks
    });
  } catch (error) {
    logSoundCloudError("getSoundCloudArtistProfile", error);

    return res.status(error.response?.status || 500).json(
      buildErrorResponse(
        "Failed to load SoundCloud artist profile.",
        error
      )
    );
  }
});

app.get("/getSoundCloudArtistTracks", async (req, res) => {
  try {
    const artist = String(req.query.artist || "").trim();
    const limit = normalizeSurfaceLimit(req.query.limit);

    if (!artist) {
      return res.status(400).json({
        message: "Missing artist query parameter"
      });
    }

    const tracks = await searchSoundCloudTracksInternal(artist, limit);

    const artistTracks = tracks.filter((track) => {
      return isSameArtist(track, artist);
    });

    return res.status(200).json({
      artistName: artist,
      source: "soundcloud",
      results: artistTracks.length > 0 ? artistTracks : tracks
    });
  } catch (error) {
    logSoundCloudError("getSoundCloudArtistTracks", error);

    return res.status(error.response?.status || 500).json(
      buildErrorResponse(
        "Failed to load SoundCloud artist tracks.",
        error
      )
    );
  }
});

app.get("/getSoundCloudApiPlaylist", async (req, res) => {
  try {
    const query = String(req.query.q || req.query.query || "").trim();
    const limit = normalizeSurfaceLimit(req.query.limit);

    if (!query) {
      return res.status(400).json({
        message: "Missing q query parameter"
      });
    }

    const tracks = await searchSoundCloudTracksInternal(query, limit);

    return res.status(200).json({
      playlist: createApiPlaylist(query, tracks),
      results: tracks
    });
  } catch (error) {
    logSoundCloudError("getSoundCloudApiPlaylist", error);

    return res.status(error.response?.status || 500).json(
      buildErrorResponse(
        "Failed to load SoundCloud API playlist.",
        error
      )
    );
  }
});

app.post("/upsertOrangeMusicUser", (req, res) => {
  try {
    const store = readOrangeStore();

    const userId = getSafeUserId(req.body.userId);
    const currentUser = store.users[userId] || {};

    const user = {
      ...currentUser,
      userId: userId,
      displayName: req.body.displayName || currentUser.displayName || "",
      username: req.body.username || currentUser.username || "",
      email: req.body.email || currentUser.email || "",
      avatarUrl: req.body.avatarUrl || currentUser.avatarUrl || "",
      bio: req.body.bio || currentUser.bio || "",
      updatedAt: Date.now(),
      createdAt: currentUser.createdAt || Date.now()
    };

    store.users[userId] = user;
    saveOrangeStore(store);

    return res.status(200).json({
      user: user
    });
  } catch (error) {
    return res.status(500).json({
      message: "Failed to upsert Orange Music user",
      detail: error.message
    });
  }
});

app.get("/getOrangeMusicUser", (req, res) => {
  try {
    const store = readOrangeStore();
    const userId = getSafeUserId(req.query.userId);
    const user = store.users[userId] || getPublicUser(userId);

    return res.status(200).json({
      user: user
    });
  } catch (error) {
    return res.status(500).json({
      message: "Failed to load Orange Music user",
      detail: error.message
    });
  }
});

app.get("/getUserApiPlaylists", (req, res) => {
  try {
    const store = readOrangeStore();
    const userId = getSafeUserId(req.query.userId);

    const playlists = Object.values(store.playlists)
      .filter((playlist) => playlist.ownerId === userId)
      .sort((a, b) => b.updatedAt - a.updatedAt);

    return res.status(200).json({
      results: playlists
    });
  } catch (error) {
    return res.status(500).json({
      message: "Failed to load user API playlists",
      detail: error.message
    });
  }
});

app.post("/createUserApiPlaylist", (req, res) => {
  try {
    const store = readOrangeStore();

    const ownerId = getSafeUserId(req.body.userId);
    const name = String(req.body.name || "").trim();

    if (!name) {
      return res.status(400).json({
        message: "Missing playlist name"
      });
    }

    const playlistId = createId("api_playlist");

    const playlist = {
      id: playlistId,
      name: name,
      description: req.body.description || "",
      source: "orange_music_soundcloud",
      sourceLabel: "Orange Music",
      ownerId: ownerId,
      ownerName: req.body.ownerName || "",
      coverUrl: req.body.coverUrl || "",
      isPublic: req.body.isPublic !== false,
      songsCount: 0,
      tracks: [],
      createdAt: Date.now(),
      updatedAt: Date.now()
    };

    store.playlists[playlistId] = playlist;
    saveOrangeStore(store);

    return res.status(201).json({
      playlist: playlist
    });
  } catch (error) {
    return res.status(500).json({
      message: "Failed to create API playlist",
      detail: error.message
    });
  }
});

app.post("/addTrackToUserApiPlaylist", (req, res) => {
  try {
    const store = readOrangeStore();

    const playlistId = String(req.body.playlistId || "").trim();
    const track = normalizeStoredTrack(req.body.track);

    if (!playlistId || !store.playlists[playlistId]) {
      return res.status(404).json({
        message: "Playlist not found"
      });
    }

    if (!track || !track.id) {
      return res.status(400).json({
        message: "Missing track"
      });
    }

    const playlist = store.playlists[playlistId];

    const exists = playlist.tracks.some((item) => item.id === track.id);

    if (!exists) {
      playlist.tracks.push(track);
    }

    playlist.coverUrl = playlist.coverUrl || track.coverUrl || "";
    playlist.songsCount = playlist.tracks.length;
    playlist.updatedAt = Date.now();

    store.playlists[playlistId] = playlist;
    saveOrangeStore(store);

    return res.status(200).json({
      playlist: playlist
    });
  } catch (error) {
    return res.status(500).json({
      message: "Failed to add track to API playlist",
      detail: error.message
    });
  }
});

app.post("/removeTrackFromUserApiPlaylist", (req, res) => {
  try {
    const store = readOrangeStore();

    const playlistId = String(req.body.playlistId || "").trim();
    const trackId = String(req.body.trackId || "").trim();

    if (!playlistId || !store.playlists[playlistId]) {
      return res.status(404).json({
        message: "Playlist not found"
      });
    }

    const playlist = store.playlists[playlistId];

    playlist.tracks = playlist.tracks.filter((track) => {
      return track.id !== trackId;
    });

    playlist.songsCount = playlist.tracks.length;
    playlist.updatedAt = Date.now();

    store.playlists[playlistId] = playlist;
    saveOrangeStore(store);

    return res.status(200).json({
      playlist: playlist
    });
  } catch (error) {
    return res.status(500).json({
      message: "Failed to remove track from API playlist",
      detail: error.message
    });
  }
});

app.get("/getSoundCloudTrackComments", (req, res) => {
  try {
    const store = readOrangeStore();

    const trackKey = getTrackKey(req.query.trackId);

    if (!trackKey) {
      return res.status(400).json({
        message: "Missing trackId query parameter"
      });
    }

    const comments = store.commentsByTrack[trackKey] || [];

    return res.status(200).json({
      trackId: getTrackDocumentId(trackKey),
      source: "orange_music_server",
      sourceLabel: "Orange Music",
      commentsSupportedByProxy: true,
      comments: comments.filter((comment) => !comment.isDeleted)
    });
  } catch (error) {
    return res.status(500).json({
      message: "Failed to load SoundCloud track comments",
      detail: error.message
    });
  }
});

app.post("/addSoundCloudTrackComment", (req, res) => {
  try {
    const store = readOrangeStore();

    const trackKey = getTrackKey(req.body.trackId);
    const content = String(req.body.content || "").trim();

    if (!trackKey) {
      return res.status(400).json({
        message: "Missing trackId"
      });
    }

    if (!content) {
      return res.status(400).json({
        message: "Missing comment content"
      });
    }

    const userId = getSafeUserId(req.body.userId);
    const user = store.users[userId] || getPublicUser(userId, req.body);

    store.users[userId] = {
      ...user,
      displayName: req.body.displayName || user.displayName || "",
      username: req.body.username || user.username || "",
      avatarUrl: req.body.avatarUrl || user.avatarUrl || "",
      updatedAt: Date.now(),
      createdAt: user.createdAt || Date.now()
    };

    const comment = {
      id: createId("comment"),
      trackId: getTrackDocumentId(trackKey),
      userId: userId,
      displayName: store.users[userId].displayName || store.users[userId].username || "",
      avatarUrl: store.users[userId].avatarUrl || "",
      content: content,
      timelinePositionMs: Number(req.body.timelinePositionMs || 0),
      likesCount: 0,
      isDeleted: false,
      createdAt: Date.now(),
      updatedAt: Date.now()
    };

    if (!Array.isArray(store.commentsByTrack[trackKey])) {
      store.commentsByTrack[trackKey] = [];
    }

    store.commentsByTrack[trackKey].push(comment);
    saveOrangeStore(store);

    return res.status(201).json({
      comment: comment
    });
  } catch (error) {
    return res.status(500).json({
      message: "Failed to add SoundCloud track comment",
      detail: error.message
    });
  }
});

app.post("/deleteSoundCloudTrackComment", (req, res) => {
  try {
    const store = readOrangeStore();

    const trackKey = getTrackKey(req.body.trackId);
    const commentId = String(req.body.commentId || "").trim();
    const userId = getSafeUserId(req.body.userId);

    if (!trackKey || !commentId) {
      return res.status(400).json({
        message: "Missing trackId or commentId"
      });
    }

    const comments = store.commentsByTrack[trackKey] || [];
    const comment = comments.find((item) => item.id === commentId);

    if (!comment) {
      return res.status(404).json({
        message: "Comment not found"
      });
    }

    if (comment.userId !== userId) {
      return res.status(403).json({
        message: "No permission to delete this comment"
      });
    }

    comment.isDeleted = true;
    comment.updatedAt = Date.now();

    saveOrangeStore(store);

    return res.status(200).json({
      comment: comment
    });
  } catch (error) {
    return res.status(500).json({
      message: "Failed to delete SoundCloud track comment",
      detail: error.message
    });
  }
});

app.get("/getSoundCloudTrackSocial", (req, res) => {
  try {
    const store = readOrangeStore();

    const trackKey = getTrackKey(req.query.trackId);
    const userId = getSafeUserId(req.query.userId);

    if (!trackKey) {
      return res.status(400).json({
        message: "Missing trackId"
      });
    }

    const likes = store.likesByTrack[trackKey] || {};
    const comments = store.commentsByTrack[trackKey] || [];

    return res.status(200).json({
      trackId: getTrackDocumentId(trackKey),
      liked: Boolean(likes[userId]),
      likesCount: Object.keys(likes).length,
      commentsCount: comments.filter((comment) => !comment.isDeleted).length
    });
  } catch (error) {
    return res.status(500).json({
      message: "Failed to load SoundCloud track social data",
      detail: error.message
    });
  }
});

app.post("/toggleSoundCloudTrackLike", (req, res) => {
  try {
    const store = readOrangeStore();

    const trackKey = getTrackKey(req.body.trackId);
    const userId = getSafeUserId(req.body.userId);

    if (!trackKey) {
      return res.status(400).json({
        message: "Missing trackId"
      });
    }

    if (!store.likesByTrack[trackKey]) {
      store.likesByTrack[trackKey] = {};
    }

    const currentlyLiked = Boolean(store.likesByTrack[trackKey][userId]);

    if (currentlyLiked) {
      delete store.likesByTrack[trackKey][userId];
    } else {
      store.likesByTrack[trackKey][userId] = {
        userId: userId,
        createdAt: Date.now()
      };
    }

    saveOrangeStore(store);

    return res.status(200).json({
      trackId: getTrackDocumentId(trackKey),
      liked: !currentlyLiked,
      likesCount: Object.keys(store.likesByTrack[trackKey]).length
    });
  } catch (error) {
    return res.status(500).json({
      message: "Failed to toggle SoundCloud track like",
      detail: error.message
    });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Orange Music streaming proxy is running on http://localhost:${PORT}`);
  console.log("Mode: progressive-only");
});
