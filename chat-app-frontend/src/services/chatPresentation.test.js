import test from "node:test";
import assert from "node:assert/strict";
import { calendarDayKey, formatDayLabel, formatLastSeen } from "./chatPresentation.js";

test("day grouping uses local calendar dates", () => {
  const first = new Date(2026, 9, 24, 23, 58);
  const next = new Date(2026, 9, 25, 0, 1);
  assert.notEqual(calendarDayKey(first), calendarDayKey(next));
  assert.equal(calendarDayKey("not-a-date"), null);
});

test("day labels handle today, yesterday, and older history", () => {
  const now = new Date(2026, 9, 24, 15);
  assert.equal(formatDayLabel(new Date(2026, 9, 24, 9), now), "Today");
  assert.equal(formatDayLabel(new Date(2026, 9, 23, 20), now), "Yesterday");
  assert.match(formatDayLabel(new Date(2026, 9, 15, 9), now), /15/);
});

test("offline presence exposes existing last-seen time", () => {
  const now = new Date(2026, 9, 24, 15);
  assert.match(formatLastSeen(new Date(2026, 9, 24, 14), now), /^Last seen today at /);
  assert.match(formatLastSeen(new Date(2026, 9, 23, 14), now), /^Last seen yesterday at /);
  assert.equal(formatLastSeen("invalid", now), "Last seen earlier");
});
