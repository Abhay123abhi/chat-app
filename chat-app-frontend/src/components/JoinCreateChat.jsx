import React, { useState } from "react";
import toast from "react-hot-toast";
import { createRoomApi, joinChatApi } from "../services/RoomService";
import useChatContext from "../context/ChatContext";
import { useNavigate } from "react-router";

const JoinCreateChat = () => {
  const [detail, setDetail] = useState({
    roomId: "",
    userName: "",
  });

  const { setRoomId, setCurrentUser, setConnected } = useChatContext();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);

  function handleFormInputChange(event) {
    setDetail({
      ...detail,
      [event.target.name]: event.target.value,
    });
  }

  function validateForm() {
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(detail.roomId.trim()) || !detail.userName.trim() || detail.userName.trim().length > 50) {
      toast.error("Use a name up to 50 characters and a room ID with letters, digits, underscores or hyphens.");
      return false;
    }
    return true;
  }

  async function joinChat() {
    if (!busy && validateForm()) {
      setBusy(true);
      try {
        const room = await joinChatApi(detail.roomId.trim());
        toast.success("Joined successfully!");
        setCurrentUser(detail.userName.trim());
        setRoomId(room.roomId);
        setConnected(true);
        navigate("/chat");
      } catch (error) {
        if (error.response?.status === 404 || error.response?.status === 400) {
          toast.error(error.response.data?.message || "Room not found");
        } else {
          toast.error("Error in joining room");
        }
      } finally {
        setBusy(false);
      }
    }
  }

  async function createRoom() {
    if (!busy && validateForm()) {
      setBusy(true);
      try {
        const response = await createRoomApi(detail.roomId.trim());
        toast.success("Room created successfully!");
        setCurrentUser(detail.userName.trim());
        setRoomId(response.roomId);
        setConnected(true);
        navigate("/chat");
      } catch (error) {
        if (error.response?.status === 409) {
          toast.error("Room already exists!");
        } else {
          toast.error("Error in creating room");
        }
      } finally {
        setBusy(false);
      }
    }
  }

  return (
    <main className="entry-page">
      <div className="entry-brand"><span className="brand-mark" aria-hidden="true">#</span> room</div>
      <section className="entry-card" aria-labelledby="entry-title">
        <p className="entry-eyebrow">A SPACE TO TALK</p>
        <h1 id="entry-title">Good conversations.<br /><span>One room away.</span></h1>
        <p className="entry-description">Pick a name, enter a room ID, and start talking.</p>
        <form onSubmit={event => { event.preventDefault(); joinChat(); }}>
          <label htmlFor="userName">Your name</label>
          <input id="userName" name="userName" autoComplete="nickname" maxLength={50}
            value={detail.userName} onChange={handleFormInputChange} placeholder="e.g. Abhay" required />
          <label htmlFor="roomId">Room ID</label>
          <input id="roomId" name="roomId" maxLength={64} autoCapitalize="none" spellCheck={false}
            pattern="[A-Za-z0-9_-]{1,64}" aria-describedby="room-help"
            value={detail.roomId} onChange={handleFormInputChange} placeholder="e.g. weekend-plans" required />
          <p id="room-help" className="field-help">Use letters, numbers, underscores or hyphens.</p>
          <button type="submit" className="entry-primary" disabled={busy}>{busy ? "Connecting…" : "Join room →"}</button>
          <button type="button" className="entry-secondary" disabled={busy} onClick={createRoom}>Create a new room</button>
        </form>
        <p className="entry-note">Guest rooms · Anyone with the room ID can join.</p>
      </section>
    </main>
  );
};

export default JoinCreateChat;
