// Pure formatting helpers shared by the chat UI and its tests.
export function calendarDayKey(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return [date.getFullYear(), date.getMonth() + 1, date.getDate()].join("-");
}

export function formatDayLabel(value, now = new Date()) {
  const date = new Date(value);
  const today = new Date(now);
  if (Number.isNaN(date.getTime()) || Number.isNaN(today.getTime())) return "Unknown date";

  const key = calendarDayKey(date);
  if (key === calendarDayKey(today)) return "Today";
  const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
  if (key === calendarDayKey(yesterday)) return "Yesterday";

  return date.toLocaleDateString(undefined, {
    weekday: "short", month: "short", day: "numeric",
    ...(date.getFullYear() !== today.getFullYear() ? { year: "numeric" } : {}),
  });
}

export function formatLastSeen(value, now = new Date()) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Last seen earlier";

  const day = formatDayLabel(date, now);
  const when = day === "Today" ? "today" : day === "Yesterday" ? "yesterday" : `on ${day}`;
  return `Last seen ${when} at ${date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}`;
}
