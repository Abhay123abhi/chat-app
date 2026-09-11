import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useNavigate } from "react-router";
import toast from "react-hot-toast";
import { FiArrowDown, FiArrowLeft, FiHash, FiSend, FiWifi } from "react-icons/fi";
import useChatContext from "../context/ChatContext";
import { getMessages, sendMessageApi } from "../services/RoomService";
import { mergeMessages } from "../services/messageState";

export default function ChatPage() {
  const { roomId, currentUser, connected, setConnected, setRoomId, setCurrentUser } = useChatContext();
  const navigate = useNavigate();
  const [messages, setMessages] = useState([]);
  const [pending, setPending] = useState([]);
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

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    if (!connected || !roomId || !currentUser) navigate("/", { replace: true });
  }, [connected, roomId, currentUser, navigate]);

  useEffect(() => {
    if (!connected || !roomId) return;
    activeRoom.current = roomId;
    let disposed = false;
    let busy = false;
    const abort = new AbortController();
    confirmedCursor.current = null;
    setMessages([]);
    setPending([]);
    setHasOlder(false);
    follow.current = true;

    function accept(items) {
      if (disposed) return;
      setMessages(previous => mergeMessages(previous, items));
      const requests = new Set(items.map(message => message.clientMessageId));
      setPending(previous => previous.filter(item => !requests.has(item.clientMessageId)));
      if (items.length && !follow.current) setNewMessages(true);
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
        synchronize();
      },
      onWebSocketClose: () => { if (!disposed) setStatus("Reconnecting"); },
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
  }, [roomId, connected]);

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
          <p>Current room</p>
          <h1>{roomId}</h1>
        </div>
        <div className="user-card">
          <span>{currentUser?.slice(0, 1)?.toUpperCase()}</span>
          <div><small>Talking as</small><strong>{currentUser}</strong></div>
        </div>
        <div className="sidebar-bottom">
          <span className={`connection-status status-${status.toLowerCase().replaceAll(" ", "-")}`}><FiWifi /> {status}</span>
          <button type="button" onClick={leave} className="leave-button"><FiArrowLeft /> Leave room</button>
        </div>
      </aside>

      <section className="conversation-panel">
        <header className="conversation-header">
          <div>
            <p>LIVE CONVERSATION</p>
            <h2><span>#</span>{roomId}</h2>
          </div>
          <p className="conversation-caption">Messages are stored before they’re broadcast live.</p>
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
              <textarea value={input} onChange={event => setInput(event.target.value)} maxLength={4000} rows={2}
                onKeyDown={event => {
                  if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
                    event.preventDefault(); send(event);
                  }
                }}
                placeholder={`Message #${roomId}`} />
            </label>
            <button disabled={!input.trim()} className="send-button" aria-label="Send message"><FiSend /></button>
          </div>
          <p>Enter to send · Shift + Enter for a new line</p>
        </form>
      </section>
    </main>
  );
}
