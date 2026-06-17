require("dotenv").config();

const express = require("express");
const axios = require("axios");
const cors = require("cors");

const app = express();

app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;
const SOUNDCLOUD_CLIENT_ID = process.env.SOUNDCLOUD_CLIENT_ID;
const SOUNDCLOUD_CLIENT_SECRET = process.env.SOUNDCLOUD_CLIENT_SECRET;

let cachedToken = null;
let cachedTokenExpiresAt = 0;

const streamCache = new Map();
const STREAM_CACHE_TTL_MS = 5 * 60 * 1000;

function checkConfig() {
  if (!SOUNDCLOUD_CLIENT_ID || !SOUNDCLOUD_CLIENT_SECRET) {
    throw new Error("Missing SOUNDCLOUD_CLIENT_ID or SOUNDCLOUD_CLIENT_SECRET in .env");
  }
}

function normalizeLimit(value) {
  const limit = Number(value || 20);

  if (Number.isNaN(limit)) return 20;
  if (limit < 1) return 1;
  if (limit > 30) return 30;

  return limit;
}

function getCachedStream(trackId) {
  const cached = streamCache.get(trackId);

  if (!cached) return null;

  if (Date.now() > cached.expiresAt) {
    streamCache.delete(trackId);
    return null;
  }

  return cached.data;
}

function setCachedStream(trackId, data) {
  streamCache.set(trackId, {
    data: data,
    expiresAt: Date.now() + STREAM_CACHE_TTL_MS
  });
}

function isRateLimitError(error) {
  return error && error.response && error.response.status === 429;
}

async function getSoundCloudAccessToken() {
  checkConfig();

  const now = Date.now();

  if (cachedToken && now < cachedTokenExpiresAt) {
    return cachedToken;
  }

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

  return cachedToken;
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

function chooseBestStreamFromStreamsEndpoint(streams) {
  if (!streams) return "";

  return (
    streams.hls_aac_160_url ||
    streams.hls_aac_96_url ||
    streams.hls_mp3_128_url ||
    streams.hls_opus_64_url ||
    streams.http_mp3_128_url ||
    ""
  );
}

function getProtocolFromUrl(url) {
  if (!url) return "";

  if (url.includes(".m3u8")) {
    return "hls";
  }

  return "progressive";
}

function getMimeTypeFromUrl(url) {
  if (!url) return "";

  if (url.includes(".m3u8")) {
    return "application/x-mpegURL";
  }

  return "audio/mpeg";
}

app.get("/", (req, res) => {
  res.json({
    message: "Orange Music SoundCloud server is running"
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

    const accessToken = await getSoundCloudAccessToken();

    const response = await axios.get("https://api.soundcloud.com/tracks", {
      headers: {
        Authorization: `OAuth ${accessToken}`
      },
      params: {
        q: query,
        limit: limit,
        access: "playable"
      }
    });

    const tracks = Array.isArray(response.data) ? response.data : [];

    const results = tracks
      .filter((track) => {
        if (track.access) {
          return track.access === "playable";
        }

        return track.streamable === true;
      })
      .filter((track) => track.id)
      .filter((track) => track.title)
      .map(mapTrackToAndroid);

    return res.status(200).json({
      results: results
    });
  } catch (error) {
    console.error("SoundCloud search failed:", error.response?.data || error.message);

    if (isRateLimitError(error)) {
      return res.status(429).json({
        message: "SoundCloud rate limited. Please try again later.",
        detail: error.response?.data || error.message
      });
    }

    return res.status(500).json({
      message: "SoundCloud search failed",
      detail: error.response?.data || error.message
    });
  }
});

app.get("/getSoundCloudStreamUrl", async (req, res) => {
  try {
    const rawTrackId = String(req.query.trackId || "").trim();
    const trackId = rawTrackId.replace("soundcloud_", "");

    if (!trackId) {
      return res.status(400).json({
        message: "Missing trackId"
      });
    }

    const cachedStream = getCachedStream(trackId);

    if (cachedStream) {
      console.log(`Return cached stream for track ${trackId}`);
      return res.status(200).json(cachedStream);
    }

    const accessToken = await getSoundCloudAccessToken();

    const trackResponse = await axios.get(
      `https://api.soundcloud.com/tracks/${trackId}`,
      {
        headers: {
          Authorization: `OAuth ${accessToken}`
        }
      }
    );

    const track = trackResponse.data;

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

    try {
      const streamsResponse = await axios.get(
        `https://api.soundcloud.com/tracks/${trackId}/streams`,
        {
          headers: {
            Authorization: `OAuth ${accessToken}`
          }
        }
      );

      streamUrl = chooseBestStreamFromStreamsEndpoint(streamsResponse.data);
      protocol = getProtocolFromUrl(streamUrl);
      mimeType = getMimeTypeFromUrl(streamUrl);

      console.log(`Streams endpoint selected: ${protocol} - ${mimeType}`);
    } catch (streamError) {
      if (isRateLimitError(streamError)) {
        return res.status(429).json({
          message: "SoundCloud rate limited. Please try again later.",
          detail: streamError.response?.data || streamError.message
        });
      }

      console.log(
        "Streams endpoint failed, fallback to transcodings:",
        streamError.response?.data || streamError.message
      );
    }

    if (!streamUrl) {
      const transcodings =
        track.media && Array.isArray(track.media.transcodings)
          ? track.media.transcodings
          : [];

      if (transcodings.length === 0) {
        return res.status(404).json({
          message: "No transcodings found"
        });
      }

      const hlsAac = transcodings.find((item) => {
        return (
          item.format &&
          item.format.protocol === "hls" &&
          item.format.mime_type &&
          item.format.mime_type.includes("audio")
        );
      });

      const hlsMp3 = transcodings.find((item) => {
        return (
          item.format &&
          item.format.protocol === "hls" &&
          item.format.mime_type === "audio/mpeg"
        );
      });

      const progressiveMp3 = transcodings.find((item) => {
        return (
          item.format &&
          item.format.protocol === "progressive" &&
          item.format.mime_type === "audio/mpeg"
        );
      });

      const selectedTranscoding =
        hlsAac || hlsMp3 || progressiveMp3 || transcodings[0];

      if (!selectedTranscoding || !selectedTranscoding.url) {
        return res.status(404).json({
          message: "Playable transcoding not found"
        });
      }

      const transcodingResponse = await axios.get(selectedTranscoding.url, {
        headers: {
          Authorization: `OAuth ${accessToken}`
        }
      });

      streamUrl =
        transcodingResponse.data && transcodingResponse.data.url
          ? transcodingResponse.data.url
          : "";

      protocol = selectedTranscoding.format?.protocol || "";
      mimeType = selectedTranscoding.format?.mime_type || "";

      console.log(`Transcoding selected: ${protocol} - ${mimeType}`);
    }

    if (!streamUrl) {
      return res.status(404).json({
        message: "Stream URL not found"
      });
    }

    const result = {
      streamUrl: streamUrl,
      protocol: protocol,
      mimeType: mimeType,
      duration: Math.floor((track.duration || 0) / 1000),
      access: track.access || "",
      streamable: track.streamable === true
    };

    setCachedStream(trackId, result);

    return res.status(200).json(result);
  } catch (error) {
    console.error("SoundCloud stream failed:", error.response?.data || error.message);

    if (isRateLimitError(error)) {
      return res.status(429).json({
        message: "SoundCloud rate limited. Please try again later.",
        detail: error.response?.data || error.message
      });
    }

    return res.status(500).json({
      message: "SoundCloud stream failed",
      detail: error.response?.data || error.message
    });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Orange Music SoundCloud server is running on http://localhost:${PORT}`);
});