// Run with the application STOPPED and after a database backup.
// mongosh "mongodb://localhost:27017/chat-app" --file scripts/migrate-history.js
const rooms = db.getCollection("rooms");
const messages = db.getCollection("messages");
const duplicates = rooms.aggregate([
  { $group: { _id: "$roomId", count: { $sum: 1 } } },
  { $match: { count: { $gt: 1 } } },
]).toArray();
if (duplicates.length) throw new Error("Duplicate room IDs must be reconciled manually before migration.");

messages.createIndex({ roomId: 1, sequence: 1 }, { unique: true, name: "room_sequence" });
messages.createIndex({ roomId: 1, clientMessageId: 1 }, { unique: true, name: "room_request" });
rooms.find({ "messages.0": { $exists: true } }).forEach(room => {
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(room.roomId)) {
    throw new Error("Rename invalid legacy room ID before migration: " + room._id);
  }
  room.messages.forEach((message, index) => {
    const sequence = index + 1;
    const id = "legacy-" + room._id + "-" + index;
    const timestamp = new Date(message.timeStamp);
    if (Number.isNaN(timestamp.getTime())) throw new Error("Invalid timestamp in room " + room._id);
    messages.updateOne(
      { roomId: room.roomId, clientMessageId: id },
      { $setOnInsert: { roomId: room.roomId, clientMessageId: id, sequence,
        sender: message.sender, content: message.content, timeStamp: timestamp } },
      { upsert: true }
    );
  });
  // Unset only after every insert succeeds. A partial run can be safely repeated.
  rooms.updateOne({ _id: room._id }, {
    $max: { nextSequence: room.messages.length }, $unset: { messages: "" }
  });
});
rooms.createIndex({ roomId: 1 }, { unique: true, name: "roomId" });
print("Migration complete. Existing timestamp BSON dates are preserved; verify counts against your backup.");
