// No external dependencies: exercise the deployed STOMP endpoint with two native WebSocket clients.
import assert from "node:assert/strict";

const ORIGIN = process.env.SMOKE_ORIGIN ?? "http://localhost:3000";
const ROOM = "ci-live-chat";
const wsUrl = ORIGIN.replace(/^http/, "ws") + "/chat";
const base = ORIGIN + "/api/v1/rooms";
const sockets = [];

function waitForFrame(socket, command, timeoutMs = 12000) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      cleanup();
      reject(new Error("Timed out waiting for STOMP " + command));
    }, timeoutMs);
    function cleanup() {
      clearTimeout(timeout);
      socket.removeEventListener("message", onMessage);
      socket.removeEventListener("error", onError);
      socket.removeEventListener("close", onClose);
    }
    function onError() { cleanup(); reject(new Error("WebSocket transport error")); }
    function onClose() { cleanup(); reject(new Error("WebSocket closed before " + command)); }
    function onMessage(event) {
      const frames = String(event.data).split("\0").map(s => s.trim()).filter(Boolean);
      for (const frame of frames) {
        const kind = frame.split("\n", 1)[0];
        if (kind === "ERROR") { cleanup(); reject(new Error("STOMP ERROR: " + frame)); return; }
        if (kind === command) { cleanup(); resolve(frame); return; }
      }
    }
    socket.addEventListener("message", onMessage);
    socket.addEventListener("error", onError);
    socket.addEventListener("close", onClose);
  });
}

async function openClient(name) {
  const socket = new WebSocket(wsUrl);
  sockets.push(socket);
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("WebSocket handshake timeout")), 12000);
    socket.addEventListener("open", () => { clearTimeout(timer); resolve(); }, {once:true});
    socket.addEventListener("error", () => { clearTimeout(timer); reject(new Error("Handshake failed")); }, {once:true});
  });
  const connected = waitForFrame(socket, "CONNECTED");
  socket.send(`CONNECT\naccept-version:1.2\nheart-beat:0,0\nroomId:${ROOM}\ndisplayName:${name}\n\n\0`);
  await connected;
  socket.send(`SUBSCRIBE\nid:messages\ndestination:/topic/room/${ROOM}\nack:auto\n\n\0`);
  // Spring simple broker does not guarantee SUBSCRIBE receipt frames.
  // Wait briefly for the subscription to register before publishing the REST message.
  await new Promise(resolve => setTimeout(resolve, 500));
  return socket;
}

try {
  const response = await fetch(base, {
    method: "POST", headers: {"Content-Type": "text/plain"}, body: ROOM
  });
  assert.ok(response.status === 201 || response.status === 409, "Create room: HTTP " + response.status);

  const alice = await openClient("Alice");
  const bob = await openClient("Bob");
  const aliceMessage = waitForFrame(alice, "MESSAGE");
  const bobMessage = waitForFrame(bob, "MESSAGE");

  const sent = await fetch(base + "/" + ROOM + "/messages", {
    method: "POST", headers: {"Content-Type": "application/json"},
    body: JSON.stringify({ clientMessageId: "stomp-ci-1", sender: "Alice", content: "native socket works" })
  });
  assert.equal(sent.status, 200, "Persist message");
  for (const [name, pending] of [["Alice", aliceMessage], ["Bob", bobMessage]]) {
    const frame = await pending;
    assert.ok(frame.includes("native socket works"), name + " did not receive the message");
  }
  console.log("Two STOMP subscribers received the persisted message.");
} finally {
  for (const socket of sockets) {
    try { socket.close(); } catch {}
  }
}
