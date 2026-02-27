import https from "https";
import http from "http";
import fs from "fs";
import path from "path";

// ================= CONFIG =================
const LISTEN_PORT = Number(process.env.PORT || 11435);
const LISTEN_HOST = (process.env.LISTEN_HOST || "").trim() || undefined;

const UPSTREAM_HOST = process.env.OLLAMA_HOST || "127.0.0.1";
const UPSTREAM_PORT = Number(process.env.OLLAMA_PORT || 11434);

const DEBUG = String(process.env.DEBUG || "").toLowerCase() === "true";

// ================= TLS =================
const certFolder = path.resolve("./certs");
const tlsOptions = {
  key: fs.readFileSync(path.join(certFolder, "current-car.com-key.pem")),
  cert: fs.readFileSync(path.join(certFolder, "current-car.com-fullchain.pem"))
};

// ================= CORS =================
function buildCorsHeaders(req) {
  const origin = req.headers.origin;

  // Reflect origin when present (works for https://current-car.com -> https://current-car.com:11435)
  // If no origin (direct navigation), keep it permissive
  const allowOrigin = origin ? origin : "*";

  return {
    "Access-Control-Allow-Origin": allowOrigin,
    "Access-Control-Allow-Headers": "authorization,content-type,x-api-key",
    "Access-Control-Allow-Methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
    "Access-Control-Expose-Headers": "*",
    Vary: origin ? "Origin" : undefined
  };
}

function writeHeadWithCors(res, statusCode, headers, cors) {
  const out = Object.assign({}, headers || {});
  Object.keys(cors).forEach((k) => {
    if (cors[k] !== undefined) out[k] = cors[k];
  });
  res.writeHead(statusCode, out);
}

function sanitizeUpstreamHeaders(req) {
  const h = {};

  // Keep only the minimum set of headers needed for Ollama
  if (req.headers["content-type"]) h["content-type"] = req.headers["content-type"];
  if (req.headers["content-length"]) h["content-length"] = req.headers["content-length"];
  if (req.headers["accept"]) h["accept"] = req.headers["accept"];
  if (req.headers["user-agent"]) h["user-agent"] = req.headers["user-agent"];

  // IMPORTANT: do not forward browser/security headers that can trigger server rules
  // (Origin, Referer, Sec-Fetch-*, etc.)
  h["host"] = `${UPSTREAM_HOST}:${UPSTREAM_PORT}`;
  return h;
}

// ================= SERVER =================
const server = https.createServer(tlsOptions, (req, res) => {
  const cors = buildCorsHeaders(req);

  // Handle preflight explicitly
  if (req.method === "OPTIONS") {
    writeHeadWithCors(res, 204, {}, cors);
    return res.end();
  }

  const url = req.url || "/";
  const upstreamReq = http.request(
    {
      host: UPSTREAM_HOST,
      port: UPSTREAM_PORT,
      method: req.method,
      path: url,
      headers: sanitizeUpstreamHeaders(req),
      timeout: 120000
    },
    (upstreamRes) => {
      const headers = Object.assign({}, upstreamRes.headers);

      // Add debug header
      headers["x-ollama-upstream"] = `http://${UPSTREAM_HOST}:${UPSTREAM_PORT}`;

      writeHeadWithCors(res, upstreamRes.statusCode || 502, headers, cors);
      upstreamRes.pipe(res);

      if (DEBUG) {
        console.log(`[OK] ${req.method} ${url} origin=${req.headers.origin || "-"} -> ${upstreamRes.statusCode}`);
      }
    }
  );

  upstreamReq.on("timeout", () => {
    upstreamReq.destroy(new Error("Upstream timeout"));
  });

  upstreamReq.on("error", (err) => {
    if (DEBUG) {
      console.log(`[ERR] ${req.method} ${url} origin=${req.headers.origin || "-"} -> ${String(err && err.message ? err.message : err)}`);
    }
    if (res.headersSent) return res.destroy(err);

    writeHeadWithCors(res, 502, { "Content-Type": "application/json" }, cors);
    res.end(JSON.stringify({ error: "Bad gateway", details: String(err && err.message ? err.message : err) }));
  });

  req.pipe(upstreamReq);
});

server.listen(LISTEN_PORT, LISTEN_HOST, () => {
  const bindInfo = LISTEN_HOST ? `${LISTEN_HOST}:${LISTEN_PORT}` : `0.0.0.0:${LISTEN_PORT}`;
  console.log(`Ollama HTTPS proxy listening on https://${bindInfo}`);
  console.log(`Upstream: http://${UPSTREAM_HOST}:${UPSTREAM_PORT}`);
});
