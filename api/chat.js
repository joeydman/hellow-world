import Anthropic from "@anthropic-ai/sdk";
import { timingSafeEqual } from "node:crypto";

const MODEL = "claude-opus-5-5";
const MAX_CONTINUATIONS = 5;
const MAX_HISTORY = 40;

const client = new Anthropic();

const TOOLS = [
  {
    type: "web_search_20260209",
    name: "web_search",
    max_uses: 8,
    user_location: {
      type: "approximate",
      city: "Sydney",
      region: "New South Wales",
      country: "AU",
      timezone: "Australia/Sydney",
    },
  },
  { type: "web_fetch_20260209", name: "web_fetch", max_uses: 5 },
];

function systemPrompt() {
  const today = new Date().toLocaleDateString("en-AU", {
    timeZone: "Australia/Sydney",
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
  });
  return [
    `Today is ${today}. The user is in Sydney, Australia and is reading on a phone.`,
    "Use Australian English. Keep answers short and direct, then offer to go deeper.",
    "Search the web whenever the question depends on current or specific facts, and say where facts came from.",
    "If you could not verify something, say so plainly rather than guessing.",
  ].join("\n");
}

function passwordOk(given) {
  const expected = process.env.APP_PASSWORD;
  if (!expected || typeof given !== "string") return false;
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

// Accept only plain-text turns from the browser, alternating and starting with the user.
function cleanHistory(raw) {
  if (!Array.isArray(raw)) return null;
  const msgs = raw
    .slice(-MAX_HISTORY)
    .filter(
      (m) =>
        m &&
        (m.role === "user" || m.role === "assistant") &&
        typeof m.content === "string" &&
        m.content.trim(),
    )
    .map((m) => ({ role: m.role, content: m.content }));
  while (msgs.length && msgs[0].role !== "user") msgs.shift();
  if (!msgs.length || msgs[msgs.length - 1].role !== "user") return null;
  return msgs;
}

export default async function handler(req, res) {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }
  if (!passwordOk(req.headers["x-app-password"])) {
    res.status(401).json({ error: "Wrong password" });
    return;
  }
  const messages = cleanHistory(req.body?.messages);
  if (!messages) {
    res.status(400).json({ error: "Bad request" });
    return;
  }

  res.writeHead(200, {
    "Content-Type": "application/x-ndjson; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Accel-Buffering": "no",
  });
  const send = (obj) => res.write(JSON.stringify(obj) + "\n");

  try {
    for (let i = 0; i <= MAX_CONTINUATIONS; i++) {
      const stream = client.beta.messages.stream({
        model: MODEL,
        max_tokens: 64000,
        betas: ["server-side-fallback-2026-07-01"],
        fallbacks: "default",
        output_config: { effort: "medium" },
        system: systemPrompt(),
        tools: TOOLS,
        messages,
      });

      for await (const event of stream) {
        if (event.type === "content_block_start") {
          const block = event.content_block;
          if (block.type === "server_tool_use") {
            send({
              t: "status",
              v: block.name === "web_fetch" ? "Reading a page…" : "Searching the web…",
            });
          }
        } else if (event.type === "content_block_delta") {
          if (event.delta.type === "text_delta") {
            send({ t: "text", v: event.delta.text });
          } else if (event.delta.type === "citations_delta") {
            const c = event.delta.citation;
            if (c?.url) send({ t: "source", v: { url: c.url, title: c.title || c.url } });
          }
        }
      }

      const final = await stream.finalMessage();
      if (final.stop_reason === "refusal") {
        send({ t: "error", v: "Claude declined to answer that one." });
        break;
      }
      if (final.stop_reason === "max_tokens") {
        send({ t: "error", v: "The answer was cut off because it was too long." });
        break;
      }
      if (final.stop_reason !== "pause_turn") break;
      // Long research turn: hand the partial turn back and the server resumes it.
      messages.push({ role: "assistant", content: final.content });
    }
  } catch (err) {
    console.error("chat error", err?.status, err?.name);
    let msg = "Something went wrong. Try again.";
    if (err instanceof Anthropic.RateLimitError) msg = "Too many requests. Wait a minute and try again.";
    else if (err instanceof Anthropic.AuthenticationError) msg = "The server's API key is missing or invalid.";
    else if (err instanceof Anthropic.APIConnectionError) msg = "Could not reach Claude. Check the connection and try again.";
    send({ t: "error", v: msg });
  }
  send({ t: "done" });
  res.end();
}
