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

const SEARCH_CACHE_TTL_MS = 2 * 60 * 1000;

// Để ngắn trong lúc test, tránh dính URL stream cũ.
const STREAM_CACHE_TTL_MS = 1 * 60 * 1000;

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

function normalizeTrackId(rawValue) {
  return String(rawValue || "")
    .trim()
    .replace("soundcloud_", "");
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
        return (status >= 200 && status < 400);
      }
    });

    const location = response.headers.location;

    if (location) {
      const finalUrl = toAbsoluteUrl(streamApiUrl, location);

      console.log("Resolved CDN URL:", finalUrl);

      if (response.data && response.data.destroy) {
        response.data.destroy();
      }

      return finalUrl;
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
        return finalUrl;
      }
    }

    logSoundCloudError("resolveProgressiveStreamUrl", error);

    return buildProxyUrl(req, "/soundcloud/proxy/media", streamApiUrl);
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

function mapTrackToAndroid(track) {
  return {
    id: `soundcloud_${track.id}`,
    soundCloudId: track.id || 0,
    title: track.title || "",
    artist: track.user ? track.user.username || "" : "",
    coverUrl: track.artwork_url || (track.user ? track.user.avatar_url || "" : ""),
    duration: Math.floor((track.duration || 0) / 1000),
    genre: track.genre || "",
    permalinkUrl: track.permalink_url || "",
    playbackCount: track.playback_count || 0,
    likesCount: track.likes_count || 0,
    streamable: track.streamable === true,
    access: track.access || ""
  };
}

// Chỉ chọn progressive MP3 để tránh rè/lặp tiếng do HLS.
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

// Chỉ lấy progressive MP3 từ streams endpoint.
function chooseBestStreamFromStreamsEndpoint(streams) {
  if (!streams) return "";

  return streams.http_mp3_128_url || "";
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

app.get("/", (req, res) => {
  res.json({
    message: "Orange Music SoundCloud server is running",
    mode: "progressive-only",
    endpoints: [
      "/health",
      "/searchSoundCloudTracks?q=lofi&limit=10",
      "/getSoundCloudStreamUrl?trackId=123456789"
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

app.get("/searchSoundCloudTracks", async (req, res) => {
  try {
    const query = String(req.query.q || "").trim();
    const limit = normalizeLimit(req.query.limit);

    if (!query) {
      return res.status(400).json({
        message: "Missing query"
      });
    }

    const cacheKey = `${query.toLowerCase()}_${limit}`;
    const cachedResult = getFromCache(searchCache, cacheKey);

    if (cachedResult) {
      console.log(`Return cached search: "${query}"`);
      return res.status(200).json(cachedResult);
    }

    const accessToken = await getSoundCloudAccessToken();

    const response = await axios.get("https://api.soundcloud.com/tracks", {
      headers: getSoundCloudHeaders(accessToken),
      params: {
        q: query,
        limit: limit,
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

    const result = {
      results: results
    };

    setToCache(searchCache, cacheKey, result, SEARCH_CACHE_TTL_MS);

    return res.status(200).json(result);
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

// Proxy audio progressive MP3.
// Android ExoPlayer sẽ gọi URL này thay vì gọi trực tiếp api.soundcloud.com.
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

app.get("/getSoundCloudStreamUrl", async (req, res) => {
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
    logSoundCloudError("getSoundCloudStreamUrl", error);

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
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Orange Music SoundCloud server is running on http://localhost:${PORT}`);
  console.log("Mode: progressive-only");
});