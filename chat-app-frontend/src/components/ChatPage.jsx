import { useEffect, useMemo, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useNavigate } from "react-router";
import toast from "react-hot-toast";
import { FiArrowDown, FiArrowLeft, FiHash, FiSend, FiUsers, FiWifi } from "react-icons/fi";
import useChatContext from "../context/ChatContext";
import { getMessages, getPresence, sendMessageApi } from "../services/RoomService";
import { mergeMessages } from "../services/messageState";

function MemberRow({ member, currentUser }) {
  const isCurrent = member.name === currentUser;
  return (
    <div className="member-row" title={member.lastSeen ? `Last seen ${new Date(member.lastSeen).toLocaleString()}` : undefined}>
      <span className="member-avatar">{member.name?.slice(0, 1)?.toUpperCase()}</span>
      <div className="member-copy">
        <strong>{member.name}{isCurrent && <em> you</em>}</strong>
        <span className={member.online ? "member-online" : "member-offline"}>
          <i></i>{member.online ? "Online" : "Offline"}
        </span>
      </div>
    </div>
  );
}

export default function ChatPage() {
  const { roomId, currentUser, connected, setConnected, setRoomId, setCurrentUser } = useChatContext();
  const navigate = useNavigate();
  const [messages, setMessages] = useState([]);
  const [pending, setPending] = useState([]);
  const [members, setMembers] = useState([]);
  const [input, setInput] = useState("");
  const [status, setStatus] = useState("Connecting");
  const [historyError, setHistoryError] = useState("");
  const [hasOlder, setHasOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [newMessages, setNewMessages] = useState(false);
  const box = useRef(null);
  const follow = useRef(true);
  const activeRoom = useRef(roomId);
  const mounted = useRef(true);
  const syncing = useRef(false);
  const syncNow = useRef(() => {});
  const confirmedCursor = useRef(null);

  const visibleMembers = useMemo(() => {
    if (members.length) return members;
    return currentUser ? [{ name: currentUser, online: status === "Live", lastSeen: null }] : [];
  }, [members, currentUser, status]);
  const onlineMembers = useMemo(() => visibleMembers.filter(member => member.online), [visibleMembers]);
  const offlineMembers = useMemo(() => visibleMembers.filter(member => !member.online), [visibleMembers]);
  const onlineCount = onlineMembers.length;

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    if (!connected || !roomId || !currentUser) navigate("/", { replace: true });
  }, [connected, roomId, currentUser, navigate]);

  useEffect(() => {
    if (!connected || !roomId || !currentUser) return;
    activeRoom.current = roomId;
    let disposed = false;
    let busy = false;
    const abort = new AbortController();
    confirmedCursor.current = null;
    setMessages([]);
    setPending([]);
    setMembers([]);
    setHasOlder(false);
    follow.current = true;

    function accept(items) {
      if (disposed) return;
      setMessages(previous => mergeMessages(previous, items));
      const requests = new Set(items.map(message => message.clientMessageId));
      setPending(previous => previous.filter(item => !requests.has(item.clientMessageId)));
      if (items.length && !follow.current) setNewMessages(true);
    }

    function acceptPresence(snapshot) {
      if (!disposed && snapshot?.roomId === roomId && Array.isArray(snapshot.members)) {
        setMembers(snapshot.members);
      }
    }

    async function synchronizePresence() {
      try {
        const snapshot = await getPresence(roomId, abort.signal);
        acceptPresence(snapshot);
      } catch (error) {
        if (!disposed && error.code !== "ERR_CANCELED") {
          // Presence is optional; message delivery should keep working without it.
        }
      }
    }

    async function synchronize() {
      if (disposed || busy) return;
      busy = true;
      syncing.current = true;
      try {
        if (confirmedCursor.current === null) {
          const page = await getMessages(roomId, { limit: 50 }, abort.signal);
          if (disposed) return;
          accept(page.messages);
          setHasOlder(page.hasMore);
          confirmedCursor.current = page.messages.at(-1)?.sequence ?? 0;
        }
        for (let pageNumber = 0; pageNumber < 20 && !disposed; pageNumber++) {
          const page = await getMessages(roomId, { after: confirmedCursor.current, limit: 100 }, abort.signal);
          if (disposed) return;
          accept(page.messages);
          if (page.nextCursor !== null) confirmedCursor.current = page.nextCursor;
          if (!page.hasMore) break;
        }
        if (!disposed) setHistoryError("");
      } catch (error) {
        if (!disposed && error.code !== "ERR_CANCELED") setHistoryError("History is unavailable. Retrying automatically.");
      } finally {
        busy = false;
        syncing.current = false;
      }
    }

    const client = new Client({
      webSocketFactory: () => new SockJS("/chat"),
      connectHeaders: { roomId, displayName: currentUser },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      connectionTimeout: 10000,
      debug: () => {},
      onConnect: () => {
        if (disposed) return;
        setStatus("Live");
        client.subscribe(`/topic/room/${roomId}`, frame => {
          try { accept([JSON.parse(frame.body)]); }
          catch { setHistoryError("A live update could not be read. History will recover it."); }
        });
        client.subscribe(`/topic/presence/${roomId}`, frame => {
          try { acceptPresence(JSON.parse(frame.body)); }
          catch { /* Presence refreshes again on reconnect. */ }
        });
        synchronizePresence();
        synchronize();
      },
      onWebSocketClose: () => {
        if (!disposed) {
          setStatus("Reconnecting");
          setMembers(previous => previous.map(member =>
            member.name === currentUser ? { ...member, online: false } : member));
        }
      },
      onStompError: () => { if (!disposed) setStatus("Connection error"); },
    });

    syncNow.current = synchronize;
    client.activate();
    synchronize();
    const interval = setInterval(synchronize, 5000);
    return () => {
      disposed = true;
      abort.abort();
      clearInterval(interval);
      syncNow.current = () => {};
      client.deactivate();
    };
  }, [roomId, currentUser, connected]);

  useEffect(() => {
    if (follow.current && box.current) box.current.scrollTop = box.current.scrollHeight;
  }, [messages, pending]);

  async function transmit(item) {
    const targetRoom = roomId;
    setPending(previous => previous.map(message =>
      message.clientMessageId === item.clientMessageId ? { ...message, status: "Sending" } : message));
    try {
      const saved = await sendMessageApi(targetRoom, {
        clientMessageId: item.clientMessageId, sender: item.sender, content: item.content,
      });
      if (!mounted.current || activeRoom.current !== targetRoom) return;
      setMessages(previous => mergeMessages(previous, [saved]));
      setPending(previous => previous.filter(message => message.clientMessageId !== item.clientMessageId));
      syncNow.current();
    } catch {
      if (!mounted.current || activeRoom.current !== targetRoom) return;
      setPending(previous => previous.map(message =>
        message.clientMessageId === item.clientMessageId ? { ...message, status: "Failed" } : message));
    }
  }

  function send(event) {
    event.preventDefault();
    if (!input.trim() || input.length > 4000) return;
    const item = { clientMessageId: crypto.randomUUID(), content: input.trim(), sender: currentUser, status: "Sending" };
    setPending(previous => [...previous, item]);
    setInput("");
    follow.current = true;
    transmit(item);
  }

  async function loadOlder() {
    if (loadingOlder || !messages.length || syncing.current) return;
    setLoadingOlder(true);
    const targetRoom = roomId;
    follow.current = false;
    try {
      const page = await getMessages(roomId, { before: messages[0].sequence, limit: 50 });
      if (!mounted.current || activeRoom.current !== targetRoom) return;
      const oldHeight = box.current.scrollHeight;
      const oldTop = box.current.scrollTop;
      setMessages(previous => mergeMessages(previous, page.messages));
      setHasOlder(page.hasMore);
      requestAnimationFrame(() => {
        if (mounted.current && activeRoom.current === targetRoom && box.current) {
          box.current.scrollTop = oldTop + box.current.scrollHeight - oldHeight;
        }
      });
    } catch { toast.error("Could not load older messages"); }
    finally { if (mounted.current) setLoadingOlder(false); }
  }

  function leave() {
    if (pending.length && !window.confirm("Unsent messages will be lost. Leave this room?")) return;
    setConnected(false);
    setRoomId("");
    setCurrentUser("");
    navigate("/");
  }

  return (
    <main className="chat-workspace">
      <aside className="chat-sidebar">
        <div className="chat-brand"><span>room</span><b>.</b></div>

        <div className="room-identity">
          <span className="room-icon"><FiHash /></span>
          <div className="room-copy">
            <p>Current room</p>
            <h1>{roomId}</h1>
          </div>
        </div>

        <section className="room-members" aria-label="Room members">
          <div className="members-heading">
            <span><FiUsers /> People</span>
            <small>{onlineCount} online</small>
          </div>

          <div className="member-list">
            {onlineMembers.length > 0 && (
              <div className="member-group">
                <p className="member-group-label"><span>Online</span><b>{onlineMembers.length}</b></p>
                {onlineMembers.map(member => <MemberRow key={`online-${member.name}`} member={member} currentUser={currentUser} />)}
              </div>
            )}

            {offlineMembers.length > 0 && (
              <div className="member-group">
                <p className="member-group-label"><span>Offline</span><b>{offlineMembers.length}</b></p>
                {offlineMembers.map(member => <MemberRow key={`offline-${member.name}`} member={member} currentUser={currentUser} />)}
              </div>
            )}
          </div>
        </section>

        <div className="sidebar-bottom">
          <span className={`connection-status status-${status.toLowerCase().replaceAll(" ", "-")}`}><FiWifi /> {status}</span>
          <button type="button" onClick={leave} className="leave-button"><FiArrowLeft /> Leave room</button>
        </div>
      </aside>

      <section className="conversation-panel">
        <header className="conversation-header">
          <div>
            <p>Conversation</p>
            <h2>Messages</h2>
          </div>
          <div className="conversation-presence">
            <span><i></i>{onlineCount} online</span>
            <small>{visibleMembers.length} {visibleMembers.length === 1 ? "person" : "people"}</small>
          </div>
        </header>

        {historyError && <p role="status" className="history-alert">{historyError}</p>}

        <section ref={box} aria-label="Conversation" className="message-stream"
          onScroll={() => {
            const element = box.current;
            follow.current = element.scrollHeight - element.scrollTop - element.clientHeight < 80;
            if (follow.current) setNewMessages(false);
          }}>
          {hasOlder && <div className="older-wrap"><button onClick={loadOlder} disabled={loadingOlder} className="older-button">
            {loadingOlder ? "Loading…" : "Load older messages"}</button></div>}

          {!messages.length && !pending.length && !historyError && (
            <div className="empty-conversation">
              <span><FiSend /></span>
              <h3>No messages yet.</h3>
              <p>Send the first one and start the room.</p>
            </div>
          )}

          {messages.map(message => {
            const own = message.sender === currentUser;
            return <article key={message.id} className={`message-row ${own ? "message-own" : "message-other"}`}>
              {!own && <div className="message-avatar">{message.sender?.slice(0, 1)?.toUpperCase()}</div>}
              <div className="message-content">
                <div className="message-meta"><strong>{message.sender}</strong><time dateTime={message.timeStamp}>{new Date(message.timeStamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</time></div>
                <div className="message-bubble"><p>{message.content}</p></div>
              </div>
            </article>;
          })}

          {pending.map(message => <article key={message.clientMessageId} className="message-row message-own message-pending">
            <div className="message-content">
              <div className="message-meta"><strong>{message.sender}</strong><span>{message.status}</span></div>
              <div className="message-bubble"><p>{message.content}</p></div>
              {message.status === "Failed" && <button type="button" onClick={() => transmit(message)} className="retry-button">Retry send</button>}
            </div>
          </article>)}
        </section>

        {newMessages && <button className="new-message-button" onClick={() => {
          follow.current = true;
          setNewMessages(false);
          box.current.scrollTop = box.current.scrollHeight;
        }}>New messages <FiArrowDown /></button>}

        <form onSubmit={send} className="composer">
          <div className="composer-inner">
            <label><span className="sr-only">Message</span>
              <textarea value={input} onChange={event => setInput(event.target.value)} maxLength={4000} rows={1}
                onKeyDown={event => {
                  if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
                    event.preventDefault(); send(event);
                  }
                }}
                placeholder={`Message #${roomId}`} />
            </label>
            <button disabled={!input.trim()} className="send-button" aria-label="Send message"><FiSend /></button>
          </div>
        </form>
      </section>
    </main>
  );
}
