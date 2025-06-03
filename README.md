# Chat App - Fullstack Application

This project is a fullstack real-time chat application built with:

- **Backend:** Java Spring Boot, MongoDB
- **Frontend:** React (Vite, TailwindCSS)
- **Database:** MongoDB
- **Containerization:** Docker & Docker Compose

---

## Project Structure

```
chat-app-backend/      # Spring Boot backend (Java)
chat-app-frontend/     # React frontend (Vite)
docker-compose.yml     # Orchestrates frontend, backend, and MongoDB containers
```

---

## Backend (`chat-app-backend`)

- **Tech Stack:** Spring Boot, MongoDB, WebSocket (STOMP)
- **Features:**
  - REST API for room creation and joining
  - WebSocket endpoint for real-time messaging
  - Stores chat rooms and messages in MongoDB

### Key Endpoints

- `POST /api/v1/rooms` - Create a new chat room
- `GET /api/v1/rooms/{roomId}` - Join an existing room
- `GET /api/v1/rooms/{roomId}/messages` - Get messages for a room (with pagination)
- WebSocket endpoint: `/chat` (STOMP over SockJS)

---

## Frontend (`chat-app-frontend`)

- **Tech Stack:** React, Vite, TailwindCSS
- **Features:**
  - Join or create chat rooms
  - Real-time messaging via WebSocket
  - Emoji picker, file upload UI, dark/light mode toggle

---

## Docker Compose (`docker-compose.yml`)

- **Services:**
  - `frontend`: Builds and serves the React app via Nginx on port **3000**
  - `backend`: Runs the Spring Boot app on port **8080**
  - `mongo`: MongoDB database on port **27017**
- **Volumes:** Persists MongoDB data

---

## How to Run on Another System

### 1. Prerequisites

- [Docker](https://www.docker.com/products/docker-desktop) and [Docker Compose](https://docs.docker.com/compose/) installed

### 2. Clone the Repository

```sh
git clone <your-repo-url>
cd <project-root>
```

### 3. Build and Start All Services

```sh
docker-compose up --build
```

- This will build and start the frontend, backend, and MongoDB containers.

### 4. Access the Application

- **Frontend:** [http://localhost:3000](http://localhost:3000)
- **Backend API:** [http://localhost:8080](http://localhost:8080)
- **MongoDB:** [localhost:27017](mongodb://localhost:27017) (for development/debugging)

### 5. Stopping the Application

Press `Ctrl+C` in the terminal, then run:

```sh
docker-compose down
```

---

## Notes

- The frontend expects the backend to be available at `http://localhost:8081` (see [`baseURL`](chat-app-frontend/src/config/AxiosHelper.js)), but Docker Compose exposes the backend at port `8080`. You may need to update the frontend config or Docker Compose ports for consistency.
- For production, update CORS and environment variables as needed.

---

## Troubleshooting

- Ensure no other services are running on ports 3000, 8080, or 27017.
- If you change code, re-run `docker-compose up --build` to rebuild images.

---

## License

This project is for educational/demo purposes.