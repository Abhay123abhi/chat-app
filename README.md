# Room Chat

A lightweight real-time guest chat application built with **React, Spring Boot and MongoDB**. Create or join a room without signing up, then chat live with persistent message history.

## What I built

- **Rooms:** Create or join by room ID and display name, without authentication.
- **Messaging:** REST-first message storage with live STOMP/WebSocket delivery, duplicate-safe retries and history pagination.
- **Presence:** Online/offline members and last-seen information for the current backend process.
- **Recovery:** Reconnect to live updates and fetch missed messages from MongoDB.
- **UI:** Responsive chat layout, message dates, connection status and send/retry feedback.

**Stack:** React 18 + Vite, Java 21 + Spring Boot 4.1.2, MongoDB 8, native STOMP/WebSocket, Docker Compose and Nginx.

## Architecture

```text
Browser (React)
  ├── REST /api/v1/... ────────┐
  └── WebSocket /ws/chat ──────┤
                               ▼
                   Nginx / Vite proxy
                               │
                               ▼
                  Spring Boot backend
                    ├── Save/load messages ──► MongoDB
                    ├── Room and presence APIs
                    └── STOMP /topic/room/{id} ──► Connected clients
```

**Message flow:** Create/join room → send message through REST → store in MongoDB → publish to subscribed clients → synchronize missed messages after reconnecting. Presence is kept in backend memory.

## Run locally

### Option A — All services in Docker (recommended)

From the repository root (requires Docker Desktop):

```powershell
docker compose up -d --build frontend
```

Compose starts **MongoDB → backend → frontend** automatically. Open **http://localhost:3000**. To inspect the database, optionally start `docker compose up -d mongo-express` and open **http://localhost:8081**.

```powershell
docker compose ps
docker compose logs -f backend
docker compose down
```

`docker compose down` stops the services but preserves MongoDB messages in the named `mongo-data` volume. Do not add `-v` unless you intend to delete stored data.

### Option B — Frontend in VS Code, backend in Docker

Run from the repository root:

```powershell
docker compose up -d --build backend
cd chat-app-frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. Vite proxies REST and WebSocket traffic to the backend on port 8080. To run Spring Boot directly in IntelliJ instead, MongoDB must be reachable from the host; the default Compose configuration does **not** publish its port 27017.

## Verify

```powershell
# From chat-app-frontend
npm run lint -- --max-warnings=0
npm test
npm run build
```

Backend tests: run `./mvnw.cmd verify` from `chat-app-backend` on Windows (Docker Desktop is needed for Testcontainers).

## Routes and scope

| Path | Purpose |
| --- | --- |
| `/` | Create/join room |
| `/chat` | React chat page |
| `/ws/chat` | STOMP WebSocket endpoint |
| `/api/v1/rooms/...` | Room, message, history and presence REST APIs |

The guest session is held in React memory: refreshing `/chat` returns to the entry page. Room IDs are **not** authentication or access control; this demo is not designed for private or sensitive conversations.
