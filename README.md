# Chat App - Fullstack Application

A real-time chat application built with:

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

## Features

- Create and join chat rooms
- Real-time messaging (WebSocket)
- Persistent chat history (MongoDB)
- Modern UI with emoji picker, file upload, and dark mode

---

## Quick Start Guide

### 1. Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop) (includes Docker Compose)
- Git

### 2. Clone the Repository

```sh
git clone https://github.com/Abhay123abhi/chat-app.git
cd chat-app
```

### 3. Build and Start All Services

```sh
docker-compose up --build
```

This command will:
- Build the frontend and backend Docker images
- Start the frontend (React), backend (Spring Boot), and MongoDB containers

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

## Configuration Notes

- The frontend expects the backend at `http://localhost:8080` (default in Docker Compose).
- If you change ports, update the frontend config in `chat-app-frontend/src/config/AxiosHelper.js`.
- MongoDB data is persisted in a Docker volume (`mongo-data`).

---

## Troubleshooting

- Make sure ports 3000, 8080, and 27017 are free.
- If you change code, re-run `docker-compose up --build` to rebuild images.
- For merge or push errors with Git, always pull remote changes first:
  ```sh
  git pull origin main --allow-unrelated-histories
  ```

---

## License

This project is for educational/demo purposes.

---

## Contribution

Feel free to fork and submit pull requests!
