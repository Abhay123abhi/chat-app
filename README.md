# Chat App

A guest-room chat application built with Java 21, Spring Boot, MongoDB, React and STOMP.

The backend persists messages before acknowledging sends. WebSocket notifications provide live updates; cursor-based history recovers missed updates. This version runs one backend instance and is intended for local demonstration, not private conversations on the public internet.

## Run with Docker

Requires Docker Desktop with Compose.

```sh
docker compose up --build
```

Open http://localhost:3000 in two browser windows, choose different display names, and join the same room. Room IDs accept letters, digits, underscores and hyphens.

MongoDB is persisted in the existing `mongo-data` volume. `docker compose down` preserves it; do not add `-v` unless you intentionally want to delete the database.

## Existing installations: migrate first

The old version stored all messages inside each room document. The new version refuses to start when non-empty embedded history is present, rather than silently hiding it.

1. Stop the old backend and make a MongoDB backup with `mongodump`. Keep the backup until you have verified room/message counts and timestamps.
2. Start only the database: `docker compose up -d mongo`.
3. From the repository root, copy and run the migration:

```sh
docker compose cp scripts/migrate-history.js mongo:/tmp/migrate-history.js
docker compose exec mongo mongosh "mongodb://localhost:27017/chat-app" --file /tmp/migrate-history.js
```

4. Start the new application with `docker compose up --build`.

If the old database is named `chatapp`, use that database name in the migration and in `SPRING_DATA_MONGODB_URI`. Never run the migration against a different database and assume the existing data moved.

The script uses deterministic legacy request IDs so interrupted runs can resume. It removes a room's embedded array only after its inserts complete. Duplicate room IDs, invalid room IDs or invalid timestamps require manual reconciliation; the script stops rather than guessing. Old BSON date values retain their instant; the old application used local timestamps, so timezone assumptions should be checked against the backup. The original old build cannot read migrated history; restore the backup before rolling back.

## Run without containers

Start MongoDB locally, then:

```sh
cd chat-app-backend
bash ./mvnw spring-boot:run
```

In another terminal:

```sh
cd chat-app-frontend
npm ci
npm run dev
```

On Windows use `mvnw.cmd` instead of `bash ./mvnw`. Vite proxies REST and SockJS to port 8080. The container uses nginx for the same-origin proxy. No frontend backend-URL edit is needed.

## What works

- Create/join guest rooms with persistent history.
- Individual message documents with indexed room sequence and request identity.
- Retry a failed send using its original request ID.
- Explicit sending, sent and failed states. Sent means stored, not read or delivered to every participant.
- Reconnect/resubscribe, history reconciliation every five seconds, and ID-based merging.
- Older history loading with an exclusive cursor.
- New-message indicator while reading older history.
- Emoji selection, multiline composer, dark mode and connection status.

Fake file-upload success and local-only pins were removed. Real attachments and shared pins should be implemented with server persistence rather than advertised as working.

## API

| Request | Result |
| --- | --- |
| POST /api/v1/rooms, text/plain room ID | Create room; duplicate ID returns 409 |
| GET /api/v1/rooms/{roomId} | Room metadata; missing room returns 404 |
| POST /api/v1/rooms/{roomId}/messages | Persist or return the original matching retry |
| GET /api/v1/rooms/{roomId}/messages?limit=50 | Latest page, displayed oldest to newest |
| GET .../messages?before=123&limit=50 | Older page |
| GET .../messages?after=123&limit=100 | Recovery page, ascending |

Send body:

```json
{"clientMessageId":"a-unique-request-id","sender":"Abhay","content":"Hello"}
```

Reuse the exact request ID, sender and content for a retry. Changing payload under the same key returns 409. The room in the URL is authoritative. Clients cannot publish directly to STOMP broker topics; WebSocket is receive-only.

History responds with `messages`, `hasMore` and `nextCursor`. Use `nextCursor` with the same direction until `hasMore` is false. Sequence gaps are allowed; do not wait for every integer.

## Tests

```sh
cd chat-app-backend
bash ./mvnw verify
```

The MongoDB Testcontainers test needs Docker and is skipped when Docker is absent. It exercises 100 concurrent unique sends, concurrent retries, persisted counts and recovery pagination. The ordinary unit tests do not need MongoDB.

```sh
cd chat-app-frontend
node --test src/services/messageState.test.js
npm run build
```

GitHub Actions runs Java 21 tests and the frontend build. No load benchmark or production-scale guarantee is implied by the test sizes.

## Manual recovery checks

1. Join one room in two windows. Send simultaneously and check persisted history after rejoining.
2. Disconnect one browser, send from the other, then reconnect. The missing messages should be reconciled without duplicating messages already received.
3. Stop the backend and send. Restart it and choose Retry. The same client request ID is reused.
4. Send more than 50 messages, then load older history. No page should overlap at its exclusive cursor.
5. Read older history while another user sends. Use the New messages button to return to the bottom.
6. Leave while sends are pending; confirm the warning. Pending drafts are held in memory and do not survive refresh.

## Scope

Guest names are not verified identities. Anyone who knows a room ID can read/join it; there is no membership authorization, private-room claim or end-to-end encryption. Keep this demo on localhost until authentication, authorization and admission limits are implemented.

See [system design and scaling plan](docs/architecture.md) for delivery semantics, failure boundaries and the path to multiple instances.
