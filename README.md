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
```

Open two browser windows, join the same room with different names, and send messages between them.

Stop the app with:

```bash
docker compose down
```

MongoDB data is stored in the `mongo-data` Docker volume, so normal `docker compose down` does not delete chat history.

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
3. Sending a message calls `POST /api/v1/rooms/{roomId}/messages`.
4. The backend stores the message in MongoDB.
5. The saved message is published to `/topic/room/{roomId}`.
6. Connected clients receive it immediately.
7. On reconnect, the client loads history and merges missed messages.

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

This is a guest-room demo, not a private messaging platform. Room IDs are not access control, users are not authenticated, and presence is kept in memory for the current backend instance.
