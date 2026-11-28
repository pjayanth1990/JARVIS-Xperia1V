const MODEL = "@cf/zai-org/glm-4.7-flash";
const IMAGE_MODEL = "@cf/black-forest-labs/flux-1-schnell";
const MAX_MESSAGES = 10;
const MAX_CONTENT = 2000;

function json(data, status = 200, origin = "*") {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": origin,
      "access-control-allow-methods": "GET,POST,OPTIONS",
      "access-control-allow-headers": "Authorization,Content-Type",
      "cache-control": "no-store"
    }
  });
}

function cors(request) {
  return {
    "access-control-allow-origin": request.headers.get("Origin") || "*",
    "access-control-allow-methods": "GET,POST,OPTIONS",
    "access-control-allow-headers": "Authorization,Content-Type",
    "access-control-max-age": "86400"
  };
}

function authorized(request, env) {
  const expected = env.JARVIS_CLIENT_TOKEN;
  const supplied = request.headers.get("Authorization") || "";
  return typeof expected === "string" && expected.length > 0 &&
    supplied === `Bearer ${expected}`;
}

async function readJson(request) {
  try { return await request.json(); }
  catch { return null; }
}

function validMessages(value) {
  if (!Array.isArray(value) || value.length < 1) return false;
  return value.slice(-MAX_MESSAGES).every(m =>
    m && ["system", "user", "assistant"].includes(m.role) &&
    typeof m.content === "string" && m.content.trim().length > 0 &&
    m.content.length <= MAX_CONTENT
  );
}

function extractModelText(result) {
  if (typeof result === "string") return result;
  if (result && typeof result.response === "string") return result.response;
  if (result && typeof result.output_text === "string") return result.output_text;
  if (result && Array.isArray(result.choices)) {
    const content = result.choices[0]?.message?.content;
    if (typeof content === "string") return content;
  }
  return "";
}

function parseIntent(text) {
  const cleaned = text.trim().replace(/^\u0060{3}(?:json)?\s*/i, "").replace(/\s*\u0060{3}$/i, "");
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  if (start < 0 || end <= start) throw new Error("Model did not return JSON");
  const parsed = JSON.parse(cleaned.slice(start, end + 1));
  const allowed = new Set(["weather", "image_generate", "image_edit", "navigation", "clarify", "general"]);
  const intent = allowed.has(parsed.intent) ? parsed.intent : "clarify";
  const confidence = Number.isFinite(Number(parsed.confidence))
    ? Math.max(0, Math.min(1, Number(parsed.confidence))) : 0;
  const entities = parsed.entities && typeof parsed.entities === "object" && !Array.isArray(parsed.entities)
    ? parsed.entities : {};
  return {
    intent,
    confidence,
    entities,
    clarification: typeof parsed.clarification === "string" ? parsed.clarification.slice(0, 500) : ""
  };
}

async function handleIntent(request, env) {
  const body = await readJson(request);
  if (!body || typeof body.input !== "string" || !body.input.trim() ||
      body.input.length > MAX_CONTENT || !validMessages(body.messages)) {
    return json({ error: "Invalid input or messages" }, 400, request.headers.get("Origin") || "*");
  }
  if (!env.AI || typeof env.AI.run !== "function") {
    return json({ error: "Cloudflare AI binding is not configured" }, 500, request.headers.get("Origin") || "*");
  }

  const instruction = `Classify the latest user request for JARVIS. Return ONLY one JSON object with keys: intent, confidence (0 to 1), entities (object), clarification (string).
Allowed intent values: weather, image_generate, image_edit, navigation, clarify, general.
Use weather only when the user asks about weather/forecast/temperature/rain; put the place in entities.city.
Use image_generate for creating a new image; put the requested visual description in entities.prompt.
Use image_edit when the user wants to modify an existing image.
Use navigation for directions, routes, nearby places or travel.
Use general for ordinary knowledge, conversation, writing, calculations and all other requests that do not require a special capability.
Use clarify only when the request is genuinely ambiguous and cannot reasonably be handled as general.
Do not treat a bare city name as a weather request unless the preceding messages establish that topic.
Respect recent conversation context and short follow-up answers.
Latest user input: ${body.input}`;

  try {
    const messages = body.messages.slice(-MAX_MESSAGES).map(m => ({
      role: m.role, content: m.content.slice(0, MAX_CONTENT)
    }));
    messages.push({ role: "user", content: instruction });
    const result = await env.AI.run(MODEL, {
      messages,
      temperature: 0.1,
      max_tokens: 400
    });
    const text = extractModelText(result);
    if (!text) throw new Error("Empty model response");
    return json(parseIntent(text), 200, request.headers.get("Origin") || "*");
  } catch (error) {
    return json({ error: "Intent classification failed", detail: String(error?.message || error).slice(0, 300) },
      502, request.headers.get("Origin") || "*");
  }
}

async function handleChat(request, env) {
  const body = await readJson(request);
  if (!body || !validMessages(body.messages)) {
    return json({ error: "Invalid messages" }, 400, request.headers.get("Origin") || "*");
  }
  if (!env.AI || typeof env.AI.run !== "function") {
    return json({ error: "Cloudflare AI binding is not configured" }, 500, request.headers.get("Origin") || "*");
  }
  try {
    const messages = body.messages.slice(-MAX_MESSAGES).map(m => ({
      role: m.role, content: m.content.slice(0, MAX_CONTENT)
    }));
    const result = await env.AI.run(MODEL, { messages, max_tokens: 1200 });
    const reply = extractModelText(result).trim();
    if (!reply) throw new Error("Empty AI response");
    return json({ reply }, 200, request.headers.get("Origin") || "*");
  } catch (error) {
    return json({ error: "AI request failed", detail: String(error?.message || error).slice(0, 300) },
      502, request.headers.get("Origin") || "*");
  }
}

async function handleImage(request, env) {
  const body = await readJson(request);
  if (!body || typeof body.prompt !== "string" || !body.prompt.trim() || body.prompt.length > 2048) {
    return json({ error: "Invalid image prompt" }, 400, request.headers.get("Origin") || "*");
  }
  if (!env.AI || typeof env.AI.run !== "function") {
    return json({ error: "Cloudflare AI binding is not configured" }, 500, request.headers.get("Origin") || "*");
  }
  try {
    const result = await env.AI.run(IMAGE_MODEL, { prompt: body.prompt.trim(), steps: 4 });
    let bytes = result;
    if (result && result.image) bytes = result.image;
    if (bytes instanceof ArrayBuffer) bytes = new Uint8Array(bytes);
    if (ArrayBuffer.isView(bytes)) {
      let binary = "";
      for (let i = 0; i < bytes.length; i += 0x8000) {
        binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
      }
      return json({ status: "success", mime_type: "image/jpeg", image_base64: btoa(binary) },
        200, request.headers.get("Origin") || "*");
    }
    if (typeof bytes === "string" && bytes.length > 0) {
      return json({ status: "success", mime_type: "image/jpeg", image_base64: bytes },
        200, request.headers.get("Origin") || "*");
    }
    throw new Error("FLUX returned no image");
  } catch (error) {
    return json({ error: "Image generation failed", detail: String(error?.message || error).slice(0, 300) },
      502, request.headers.get("Origin") || "*");
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors(request) });

    if (request.method === "GET" && url.pathname === "/") {
      return json({
        status: "JARVIS backend online",
        text_ai: "Cloudflare Workers AI",
        image_ai: "Cloudflare FLUX.1 schnell",
        intent_ai: "Cloudflare Workers AI"
      }, 200, request.headers.get("Origin") || "*");
    }

    if (!["/chat", "/image", "/intent"].includes(url.pathname)) {
      return json({ error: "Not found" }, 404, request.headers.get("Origin") || "*");
    }
    if (request.method !== "POST") return json({ error: "Method not allowed" }, 405, request.headers.get("Origin") || "*");
    if (!authorized(request, env)) return json({ error: "Unauthorized" }, 401, request.headers.get("Origin") || "*");

    if (url.pathname === "/chat") return handleChat(request, env);
    if (url.pathname === "/image") return handleImage(request, env);
    return handleIntent(request, env);
  }
};
