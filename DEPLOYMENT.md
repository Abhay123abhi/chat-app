# Free public deployment

The public demo runs as one Render Free web service plus one MongoDB Atlas Free cluster.

The production image builds the React frontend and packages it into Spring Boot, so the UI, REST API and SockJS/WebSocket traffic use the same origin. The browser route remains `/chat`; the socket endpoint is `/ws-chat`.

## Public-demo limits

The checked-in Render Blueprint uses conservative limits:

- 60 room API requests per IP per minute
- 300 room API requests globally per minute
- 20 messages per IP per minute
- 60 messages globally per minute
- 4-message burst per 5 seconds per IP
- 3 room creations per IP per hour
- 10 room creations globally per hour
- 1,000 lifetime messages per room
- 25 active rooms
- 20 active display names per room
- 30 remembered presence members per room
- 5 WebSocket connections per IP
- 22 WebSocket connections per room
- 50 WebSocket connections globally
- 7-day message retention
- 7-day inactive-room expiry
- history pages capped at 100 messages

Emergency switches:

```text
CHAT_ENABLED=false
ROOM_CREATION_ENABLED=false
```

## 1. Merge only after CI is green

The PR must pass:

- backend tests
- frontend tests/build
- Docker Compose smoke test
- production Render-image build

## 2. Create MongoDB Atlas Free

1. Sign in to MongoDB Atlas and create a project.
2. Create a Free cluster.
3. Create a database user with a generated password.
4. Temporarily allow `0.0.0.0/0` in Network Access for the first deployment.
5. Copy the SRV connection string and use database `chatapp`.
6. Add a small connection pool.

Connection-string shape:

```text
mongodb+srv://USER:PASSWORD@CLUSTER/chatapp?retryWrites=true&w=majority&maxPoolSize=20&minPoolSize=0&maxIdleTimeMS=60000
```

Do not commit the real URI.

## 3. Deploy with Render Blueprint

1. Sign in to Render and connect GitHub.
2. Choose **New > Blueprint**.
3. Select this repository.
4. Render reads `render.yaml`.
5. Confirm the service uses the **Free** plan and Singapore region.
6. Enter `SPRING_DATA_MONGODB_URI` when Render asks for the secret value.
7. Create the Blueprint.
8. Wait for the build and `/actuator/health` health check to pass.

The app reads Render's `PORT` automatically. The production JVM is constrained for a 512 MB instance.

## 4. Restrict Atlas network access

After Render creates the service:

1. Open the Render service.
2. Open **Connect > Outbound**.
3. Copy all outbound CIDR ranges.
4. Open Atlas **Network Access**.
5. Add those Render CIDRs.
6. Remove the temporary `0.0.0.0/0` rule.

## 5. Verify production

Test from the public Render URL:

1. Create a room.
2. Join it in two browser windows/devices.
3. Send messages both ways.
4. Refresh the browser on `/chat`; the SPA should reload.
5. Verify reconnect/history recovery.
6. Verify `/actuator/health` returns HTTP 200.
7. Send messages rapidly and confirm HTTP 429.
8. Try more than five simultaneous socket sessions from one IP and confirm later connections are rejected.
9. Set `ROOM_CREATION_ENABLED=false`; creating rooms should stop while existing rooms continue.
10. Set `CHAT_ENABLED=false`; room API calls and new socket connections should stop.
11. Restore both switches to `true`.

## 6. Free-tier behavior

Render Free services sleep after inactivity, so the first request after idle can have a cold start. MongoDB is external because Render's local filesystem is ephemeral.

App limits reduce abuse but cannot read Render billing counters in real time. For a strict no-surprise-cost demo, use a Render workspace without a payment method and monitor provider usage. Provider-side suspension is stronger than trying to predict the exact byte where a free allowance ends.
