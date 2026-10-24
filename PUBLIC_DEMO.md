# Optional public-demo hardening (not deployed)

This branch was created from the current `main` instead of merging legacy PR #13. It preserves **Java 21, Spring Boot 4.1.2, React 18, MongoDB 8, native STOMP on `/ws/chat`, and React Router on `/chat`**. PR #13 is obsolete and should not be merged into this branch.

## Protection goals

- Rate-limit all room REST requests and stricter limits for room creation and message sends.
- Reject bursts and cap concurrent STOMP sessions, per room and globally.
- Limit MongoDB room count, message count per room, remembered presence, and message size (existing application validation).
- Expire records after a configured retention window using MongoDB TTL indexes, and provide kill switches.
- Keep all of this **disabled for local development** unless explicitly enabled.

## Enable only on an isolated demo deployment

Set `CHAT_GUARD_ENABLED=true`. Defaults are in `GuardProperties.java` and can be overridden by environment variables, e.g.:

```text
CHAT_GUARD_ENABLED=true
CHAT_GUARD_CHAT_ENABLED=true
CHAT_GUARD_ROOM_CREATION_ENABLED=true
CHAT_GUARD_MAX_ROOMS=25
CHAT_GUARD_MAX_MESSAGES_PER_ROOM=1000
CHAT_GUARD_RETENTION_DAYS=7
CHAT_GUARD_REQUESTS_PER_IP_PER_MINUTE=60
CHAT_GUARD_REQUESTS_GLOBAL_PER_MINUTE=300
CHAT_GUARD_MESSAGES_PER_IP_PER_MINUTE=20
CHAT_GUARD_MESSAGES_GLOBAL_PER_MINUTE=60
CHAT_GUARD_MESSAGE_BURST_PER_FIVE_SECONDS=4
CHAT_GUARD_ROOM_CREATIONS_PER_IP_PER_HOUR=3
CHAT_GUARD_ROOM_CREATIONS_GLOBAL_PER_HOUR=10
CHAT_GUARD_MAX_CONNECTIONS_PER_IP=5
CHAT_GUARD_MAX_CONNECTIONS_PER_ROOM=22
CHAT_GUARD_MAX_CONNECTIONS_GLOBAL=50
```

Set `CHAT_GUARD_ROOM_CREATION_ENABLED=false` to pause room creation, or `CHAT_GUARD_CHAT_ENABLED=false` to reject new REST activity and WebSocket connections.

## Security caveats — read before public deployment

**Do not claim this prevents all DDoS/abuse.** In-process memory limits work for a single application instance only. Add provider/CDN-level throttling, request-body limits, connection limits, database quotas, budgets/usage alerts, and monitoring. The rate limiter intentionally ignores `X-Forwarded-For` to avoid trusting attacker-controlled headers. Behind a reverse proxy, all users may share one IP and per-client quotas may collapse into one global-ish quota. Proper client-IP extraction requires an explicit allowlist of trusted reverse proxy addresses and correct forwarding headers; test this on the chosen provider.

Room count limits are not atomic across multiple instances; Mongo TTL deletion is asynchronous and may take time. Expiring a room without deleting its messages can leave records until message TTL cleanup. In-memory counters reset on service restart. Because a room ID is not authentication, public rooms are accessible to anyone who knows or guesses the ID. Add provider-side abuse protection before publishing a broadly advertised URL.

This branch is a **future deployment candidate**, not production-approved. Test burst traffic, reconnect behavior, TTL indexes, normal room capacity behavior, and multiple browser sessions on staging first. Keep the older PR unmerged.
