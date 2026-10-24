import test from "node:test";
import assert from "node:assert/strict";
import { mergeMessages } from "./messageState.js";

test("history arriving after live messages preserves both and deduplicates", () => {
  const live = [{ id: "b", sequence: 2 }, { id: "c", sequence: 3 }];
  const history = [{ id: "a", sequence: 1 }, { id: "b", sequence: 2 }];
  assert.deepEqual(mergeMessages(live, history).map(item => item.id), ["a", "b", "c"]);
  assert.equal(live.length, 2);
});
test("a repeated notification does not create a duplicate", () => {
  const message = { id: "a", sequence: 1 };
  assert.equal(mergeMessages([message], [message, message]).length, 1);
});

test("out-of-order recovery merges into correct chronological order", () => {
  const earlier = [{ id: "third", sequence: 3, content: "three" }];
  const late = [
    { id: "second", sequence: 2, content: "two" },
    { id: "first", sequence: 1, content: "one" },
  ];
  assert.deepEqual(mergeMessages(earlier, late).map(item => item.sequence), [1, 2, 3]);
});

test("pagination overlap and live websocket replay remain idempotent", () => {
  const initial = [{ id: "m3", sequence: 3 }, { id: "m4", sequence: 4 }];
  const older = [{ id: "m1", sequence: 1 }, { id: "m2", sequence: 2 }, { id: "m3", sequence: 3 }];
  const replay = [{ id: "m4", sequence: 4 }, { id: "m5", sequence: 5 }];
  const combined = mergeMessages(mergeMessages(initial, older), replay);
  assert.deepEqual(combined.map(item => item.id), ["m1", "m2", "m3", "m4", "m5"]);
  assert.equal(new Set(combined.map(item => item.id)).size, combined.length);
});

test("a confirmed REST response and matching WebSocket message do not duplicate", () => {
  const saved = { id: "server-1", clientMessageId: "request-1", sequence: 7 };
  assert.deepEqual(mergeMessages([saved], [saved]), [saved]);
});

test("fresh server copy replaces stale entry without changing its position", () => {
  const stale = { id: "a", sequence: 1, content: "old" };
  const fresh = { ...stale, content: "new" };
  assert.deepEqual(mergeMessages([stale, { id: "b", sequence: 2 }], [fresh])[0], fresh);
});
