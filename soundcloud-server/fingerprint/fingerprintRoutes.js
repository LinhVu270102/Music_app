const express = require("express");

const {
  processSongFingerprint,
  searchAudioFingerprint,
  getFpcalcPath
} = require("./fingerprintService");
const { resolveServiceAccountPath } = require("./firebaseAdmin");

function createFingerprintRouter() {
  const router = express.Router();

  router.get("/health", (req, res) => {
    res.json({
      status: "ok",
      fpcalcPath: getFpcalcPath(),
      firebaseServiceAccountPath: resolveServiceAccountPath()
    });
  });

  router.post("/songs/:songId/process", async (req, res) => {
    try {
      const shouldProcessAsync =
        req.query.async === "true" ||
        req.body?.async === true;

      if (shouldProcessAsync) {
        processSongFingerprint(req.params.songId).catch((error) => {
          console.error(
            "Async fingerprint processing failed:",
            req.params.songId,
            error.message
          );
        });

        return res.status(202).json({
          songId: req.params.songId,
          status: "queued"
        });
      }

      const result = await processSongFingerprint(req.params.songId);
      return res.status(200).json(result);
    } catch (error) {
      return res.status(error.statusCode || 500).json({
        message: error.message || "Fingerprint processing failed.",
        detail: error.detail || ""
      });
    }
  });

  router.post("/search", async (req, res) => {
    try {
      const result = await searchAudioFingerprint(req.body || {});
      return res.status(200).json(result);
    } catch (error) {
      return res.status(error.statusCode || 500).json({
        message: error.message || "Audio fingerprint search failed.",
        detail: error.detail || ""
      });
    }
  });

  return router;
}

module.exports = {
  createFingerprintRouter
};
