# Room Chat

A small real-time guest chat app built with Spring Boot, MongoDB, React and STOMP/WebSocket.

Users can create a room, join with a display name, send messages, see online/offline room presence and recover missed messages after reconnecting.

## Stack

- Java 21 + Spring Boot
- MongoDB
- React + Vite
- STOMP over SockJS
- Docker Compose

## How it works

```text
React client
   |
   | REST: create/join room, send message, load history
   v
Spring Boot
   |
   | persist
   v
MongoDB

Spring Boot -- STOMP/WebSocket --> connected room members
```

Messages are written through the REST API first. After MongoDB stores a message, the backend publishes it to the room over STOMP. If a client reconnects, it loads message history again and merges anything it missed.

Presence is session based: a connected WebSocket session is Online. Users seen during the current backend process can appear Offline after disconnecting.

## Public demo guardrails

The free/public deployment is intentionally single-instance, but abuse controls are isolated behind interfaces so their in-memory implementations can later be replaced by distributed implementations without changing controllers or the message model.

Default server-side limits:

```text
30 messages / IP / minute
5-message burst / 5 seconds / IP
5 room creations / IP / hour
120 room API requests / IP / minute
5 WebSocket connections / IP
20 active users / room
2,000 lifetime messages / room
7-day message retention
7-day room inactivity expiry
history pages capped at 100 messages
```

MongoDB TTL indexes use per-document `expiresAt` timestamps, so changing the configured retention applies to newly created rooms/messages without recreating the index.

All limits are configurable through environment variables:

```text
CHAT_ENABLED=true
ROOM_CREATION_ENABLED=true
MESSAGE_RATE_LIMIT_PER_MINUTE=30
MESSAGE_BURST_LIMIT=5
MESSAGE_BURST_WINDOW_SECONDS=5
ROOM_CREATION_RATE_LIMIT_PER_HOUR=5
MAX_MESSAGE_LENGTH=4000
MAX_MESSAGES_PER_ROOM=2000
MAX_ACTIVE_USERS_PER_ROOM=20
MAX_WEBSOCKET_CONNECTIONS_PER_IP=5
MESSAGE_RETENTION_DAYS=7
ROOM_INACTIVITY_DAYS=7
GLOBAL_REQUEST_RATE_LIMIT_PER_MINUTE=120
DEFAULT_HISTORY_PAGE_SIZE=50
MAX_HISTORY_PAGE_SIZE=100
```

For a no-surprise-cost public demo, use free hosting with no payment method attached where the provider supports suspension at quota exhaustion. Keep Mongo Express local only; never expose it publicly.

## Run locally

The easiest way is Docker Desktop.

```bash
git clone https://github.com/Abhay123abhi/chat-app.git
cd chat-app
docker compose up --build
```

Open:

```text
http://localhost:3000
http://localhost:8081  -- mongo express for local GUI only
```

Open two browser windows, join the same room with different names, and send messages between them.

Stop the app with:

```bash
docker compose down
```

MongoDB data is stored in the `mongo-data` Docker volume, so normal `docker compose down` does not delete chat history immediately. TTL cleanup removes expired room/message documents while MongoDB is running.

## Run for development

Start MongoDB:

```bash
docker compose up -d mongo
```

Run the backend from `chat-app-backend`:

```powershell
./mvnw.cmd spring-boot:run
```

Run the frontend from `chat-app-frontend`:

```bash
npm ci
npm run dev
```

Then open the Vite URL, normally `http://localhost:5173`.

## Main flow

1. Create or join a room.
2. The browser connects to `/chat` using STOMP/SockJS.
3. WebSocket admission checks room existence, per-IP connection limits and the room active-user limit.
4. Sending a message calls `POST /api/v1/rooms/{roomId}/messages`.
5. The backend applies global, burst and message rate limits.
6. The backend stores the message in MongoDB with idempotency, ordering, a room quota and expiry.
7. The saved message is published to `/topic/room/{roomId}`.
8. Connected clients receive it immediately.
9. On reconnect, the client loads history and merges missed messages.

## Useful endpoints

```text
POST /api/v1/rooms
GET  /api/v1/rooms/{roomId}
POST /api/v1/rooms/{roomId}/messages
GET  /api/v1/rooms/{roomId}/messages
GET  /api/v1/rooms/{roomId}/presence
```

## Tests

Backend:

```bash
cd chat-app-backend
./mvnw verify
```

Frontend:

```bash
cd chat-app-frontend
node --test src/services/messageState.test.js
npm run build
```

## Current scope

This is a guest-room, not a private messaging platform. Room IDs are not authentication, users are not registered, and the current free-deployment implementation uses in-memory rate-limit/presence state for one backend instance.

The boundaries are intentionally replaceable: a future multi-instance deployment can provide distributed implementations for rate limiting, presence and realtime fan-out while preserving the existing REST contract, idempotent message model, MongoDB persistence and reconnect recovery.
