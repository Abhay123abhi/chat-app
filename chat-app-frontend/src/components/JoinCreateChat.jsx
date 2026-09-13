import React, { useState } from "react";
import toast from "react-hot-toast";
import { createRoomApi, joinChatApi } from "../services/RoomService";
import useChatContext from "../context/ChatContext";
import { useNavigate } from "react-router";
import { FiArrowRight, FiHash, FiMessageCircle } from "react-icons/fi";

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
      <section className="entry-visual" aria-label="Room messaging overview">
        <div className="entry-brand">
          <span className="brand-mark" aria-hidden="true"><FiMessageCircle /></span>
          <span>room<span className="brand-dot">.</span></span>
        </div>

        <div className="entry-hero-copy">
          <p className="entry-kicker">LIVE ROOMS · NO SIGN-UP</p>
          <h1>Less feed.<br />More <em>talk.</em></h1>
          <p>Fast guest conversations with durable messages and live updates.</p>
        </div>

        <div className="entry-demo" aria-hidden="true">
          <div className="demo-grid"></div>
          <div className="demo-traffic-line traffic-line-a"></div>
          <div className="demo-traffic-line traffic-line-b"></div>

          <div className="demo-bubble demo-bubble-a"><span>AJ</span><p>Shipping the fix now.</p></div>
          <div className="demo-bubble demo-bubble-b"><span>RK</span><p>Got it — I’m in the room.</p></div>

          <div className="traffic-message traffic-message-a"><span>NM</span><p>Deploy is green ✓</p></div>
          <div className="traffic-message traffic-message-b"><span>SK</span><p>Joining now.</p></div>
          <div className="traffic-message traffic-message-c"><span>DV</span><p>Looks good to me.</p></div>
          <div className="traffic-message traffic-message-d"><span>PM</span><p>One sec…</p></div>

          <div className="typing-packet">
            <span>TS</span>
            <i></i><i></i><i></i>
          </div>

          <div className="demo-signal"><i></i><span>live</span></div>
          <div className="message-counter"><strong>+4</strong><span>messages</span></div>
        </div>

        <div className="entry-features" aria-label="Highlights">
          <span>Guest rooms</span>
          <span>Realtime updates</span>
          <span>Persistent history</span>
        </div>
      </section>

      <section className="entry-panel">
        <div className="entry-card" aria-labelledby="entry-title">
          <p className="entry-eyebrow">ENTER A CONVERSATION</p>
          <h2 id="entry-title">Your room is one ID away.</h2>
          <p className="entry-description">Use a display name and room ID. Join an existing conversation or create a fresh one.</p>

          <form onSubmit={event => { event.preventDefault(); joinChat(); }}>
            <div className="entry-field">
              <label htmlFor="userName">Display name</label>
              <input id="userName" name="userName" autoComplete="nickname" maxLength={50}
                value={detail.userName} onChange={handleFormInputChange} placeholder="Abhay" required />
            </div>

            <div className="entry-field">
              <label htmlFor="roomId">Room ID</label>
              <div className="room-input-wrap">
                <FiHash aria-hidden="true" />
                <input id="roomId" name="roomId" maxLength={64} autoCapitalize="none" spellCheck={false}
                  pattern="[A-Za-z0-9_-]{1,64}" aria-describedby="room-help"
                  value={detail.roomId} onChange={handleFormInputChange} placeholder="weekend-plans" required />
              </div>
              <p id="room-help" className="field-help">Letters, numbers, underscores or hyphens.</p>
            </div>

            <button type="submit" className="entry-primary" disabled={busy}>
              <span>{busy ? "Connecting…" : "Join room"}</span>
              {!busy && <FiArrowRight aria-hidden="true" />}
            </button>
            <button type="button" className="entry-secondary" disabled={busy} onClick={createRoom}>Create this room instead</button>
          </form>

          <p className="entry-note"><span>●</span> Guest room · Anyone with the room ID can join.</p>
        </div>
      </section>
    </main>
  );
};

export default JoinCreateChat;
