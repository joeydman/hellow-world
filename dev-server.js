// Local preview: `npm run dev`, then open http://localhost:3000
// Needs ANTHROPIC_API_KEY and APP_PASSWORD set in your shell.
import http from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import handler from "./api/chat.js";

const TYPES = { ".html": "text/html", ".svg": "image/svg+xml", ".png": "image/png", ".webmanifest": "application/manifest+json" };
const root = join(import.meta.dirname, "public");

http.createServer(async (req, res) => {
  res.status = (code) => { res.statusCode = code; return res; };
  res.json = (obj) => { res.setHeader("Content-Type", "application/json"); res.end(JSON.stringify(obj)); };
  if (req.url === "/api/chat") {
    let body = "";
    for await (const chunk of req) body += chunk;
    try { req.body = JSON.parse(body || "{}"); } catch { req.body = {}; }
    return handler(req, res);
  }
  const path = normalize(req.url === "/" ? "/index.html" : req.url.split("?")[0]);
  try {
    const data = await readFile(join(root, path));
    res.writeHead(200, { "Content-Type": TYPES[extname(path)] || "application/octet-stream" });
    res.end(data);
  } catch {
    res.writeHead(404); res.end("Not found");
  }
}).listen(3000, () => console.log("http://localhost:3000"));
