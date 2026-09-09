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
