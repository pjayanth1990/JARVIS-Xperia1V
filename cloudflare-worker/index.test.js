import test from "node:test";
import assert from "node:assert/strict";
import worker from "./index.js";

const token = "test-secret";
const env = {
  JARVIS_CLIENT_TOKEN: token,
  AI: {
    async run(model, input) {
      if (model.includes("glm")) {
        const last = input.messages.at(-1).content;
        if (last.includes("Latest user input: Weather in Bengaluru")) {
          return { response: '{"intent":"weather","confidence":0.98,"entities":{"city":"Bengaluru"},"clarification":""}' };
        }
        return { response: '{"intent":"general","confidence":0.91,"entities":{},"clarification":""}' };
      }
      return new Uint8Array([1, 2, 3]);
    }
  }
};
const request = (path, body, auth = true) => new Request("https://jarvis.example" + path, {
  method: "POST",
  headers: { "content-type": "application/json", ...(auth ? { authorization: "Bearer " + token } : {}) },
  body: JSON.stringify(body)
});

test("health endpoint advertises all three AI routes", async () => {
  const response = await worker.fetch(new Request("https://jarvis.example/"), env);
  const data = await response.json();
  assert.equal(response.status, 200);
  assert.equal(data.status, "JARVIS backend online");
  assert.equal(data.intent_ai, "Cloudflare Workers AI");
});

test("intent endpoint returns the Android app contract", async () => {
  const response = await worker.fetch(request("/intent", {
    input: "Weather in Bengaluru",
    messages: [{ role: "user", content: "Weather in Bengaluru" }]
  }), env);
  const data = await response.json();
  assert.equal(response.status, 200);
  assert.equal(data.intent, "weather");
  assert.equal(data.entities.city, "Bengaluru");
  assert.equal(typeof data.confidence, "number");
});

test("general questions route to general instead of clarification", async () => {
  const response = await worker.fetch(request("/intent", {
    input: "Explain how a jet engine works",
    messages: [{ role: "user", content: "Explain how a jet engine works" }]
  }), env);
  const data = await response.json();
  assert.equal(data.intent, "general");
});

test("protected routes reject missing credentials", async () => {
  const response = await worker.fetch(request("/intent", {
    input: "Hello", messages: [{ role: "user", content: "Hello" }]
  }, false), env);
  assert.equal(response.status, 401);
});

test("intent validates empty messages", async () => {
  const response = await worker.fetch(request("/intent", { input: "Hello", messages: [] }), env);
  assert.equal(response.status, 400);
});

test("unknown routes return 404", async () => {
  const response = await worker.fetch(new Request("https://jarvis.example/unknown", {
    method: "POST", headers: { authorization: "Bearer " + token }
  }), env);
  assert.equal(response.status, 404);
});

test("chat endpoint returns a non-empty reply", async () => {
  const response = await worker.fetch(request("/chat", {
    messages: [{ role: "system", content: "You are JARVIS." }, { role: "user", content: "Explain gravity simply." }]
  }), env);
  const data = await response.json();
  assert.equal(response.status, 200);
  assert.equal(typeof data.reply, "string");
  assert.ok(data.reply.length > 0);
});

test("image endpoint returns an image payload", async () => {
  const response = await worker.fetch(request("/image", { prompt: "A silver futuristic assistant emblem" }), env);
  const data = await response.json();
  assert.equal(response.status, 200);
  assert.equal(data.status, "success");
  assert.equal(data.mime_type, "image/jpeg");
  assert.ok(data.image_base64.length > 0);
});

test("chat endpoint rejects malformed message roles", async () => {
  const response = await worker.fetch(request("/chat", {
    messages: [{ role: "tool", content: "untrusted tool output" }]
  }), env);
  assert.equal(response.status, 400);
});

test("intent endpoint rejects oversized user input", async () => {
  const input = "x".repeat(2001);
  const response = await worker.fetch(request("/intent", {
    input, messages: [{ role: "user", content: "short" }]
  }), env);
  assert.equal(response.status, 400);
});

test("protected routes reject an incorrect token", async () => {
  const response = await worker.fetch(new Request("https://jarvis.example/chat", {
    method: "POST",
    headers: { "content-type": "application/json", authorization: "Bearer wrong-token" },
    body: JSON.stringify({ messages: [{ role: "user", content: "Hello" }] })
  }), env);
  assert.equal(response.status, 401);
});
