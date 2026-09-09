import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useNavigate } from "react-router";
import toast from "react-hot-toast";
import EmojiPicker from "emoji-picker-react";
import useChatContext from "../context/ChatContext";
import { getMessages, sendMessageApi } from "../services/RoomService";
import { mergeMessages } from "../services/messageState";

export default function ChatPage() {
  const { roomId, currentUser, connected, setConnected, setRoomId, setCurrentUser } = useChatContext();
  const navigate = useNavigate();
  const [messages, setMessages] = useState([]);
  const [pending, setPending] = useState([]);
  const [input, setInput] = useState("");
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [status, setStatus] = useState("Connecting");
  const [historyError, setHistoryError] = useState("");
  const [hasOlder, setHasOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [newMessages, setNewMessages] = useState(false);
  const [dark, setDark] = useState(() => localStorage.getItem("chat-theme") === "dark");
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
    localStorage.setItem("chat-theme", dark ? "dark" : "light");
  }, [dark]);

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
        // Only history advances the recovery cursor. A newer socket event cannot skip a missing message.
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

  return <div className={dark ? "dark" : ""}>
    <div className="relative flex h-dvh flex-col bg-gray-100 text-gray-900 dark:bg-gray-900 dark:text-gray-100">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-300 bg-white px-5 py-4 dark:border-gray-700 dark:bg-gray-800">
        <div><h1 className="text-lg font-semibold break-all">Room: {roomId}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-300">{currentUser} · Guest room</p></div>
        <div className="flex items-center gap-4">
          <span role="status" className="text-sm">{status}</span>
          <button type="button" onClick={() => setDark(value => !value)} className="rounded border px-3 py-2">{dark ? "Light" : "Dark"}</button>
          <button type="button" onClick={leave} className="rounded border px-3 py-2">Leave</button>
        </div>
      </header>
      {historyError && <p role="status" className="bg-amber-100 px-5 py-2 text-sm text-amber-900">{historyError}</p>}
      <main ref={box} aria-label="Conversation" className="min-h-0 flex-1 overflow-y-auto p-4 md:px-8"
        onScroll={() => {
          const element = box.current;
          follow.current = element.scrollHeight - element.scrollTop - element.clientHeight < 80;
          if (follow.current) setNewMessages(false);
        }}>
        {hasOlder && <div className="mb-4 text-center"><button onClick={loadOlder} disabled={loadingOlder} className="rounded border px-4 py-2 disabled:opacity-50">
          {loadingOlder ? "Loading…" : "Load older messages"}</button></div>}
        {!messages.length && !pending.length && !historyError && <p className="py-10 text-center text-gray-500">Messages will appear here.</p>}
        {messages.map(message => <article key={message.id} className={`mb-3 flex ${message.sender === currentUser ? "justify-end" : "justify-start"}`}>
          <div className={`max-w-[85%] rounded-xl px-4 py-3 md:max-w-[65%] ${message.sender === currentUser ? "bg-blue-700 text-white" : "bg-white dark:bg-gray-800"}`}>
            <p className="text-sm font-semibold">{message.sender}</p>
            <p className="whitespace-pre-wrap break-words py-1">{message.content}</p>
            <p className="text-xs opacity-80"><time dateTime={message.timeStamp}>{new Date(message.timeStamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</time> · Sent</p>
          </div>
        </article>)}
        {pending.map(message => <div key={message.clientMessageId} className="mb-3 ml-auto max-w-[85%] rounded-xl border border-dashed p-3 md:max-w-[65%]">
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
          <span role="status" className="text-sm">{message.status}</span>
          {message.status === "Failed" && <button type="button" onClick={() => transmit(message)} className="ml-3 underline">Retry</button>}
        </div>)}
      </main>
      {newMessages && <button className="self-center rounded-full bg-blue-700 px-4 py-2 text-white" onClick={() => {
        follow.current = true;
        setNewMessages(false);
        box.current.scrollTop = box.current.scrollHeight;
      }}>New messages ↓</button>}
      {showEmojiPicker && <div className="absolute bottom-28 left-4 z-10"><EmojiPicker width="min(350px, 90vw)" height={350} onEmojiClick={emoji => {
        setInput(value => (value + emoji.emoji).slice(0, 4000));
        setShowEmojiPicker(false);
      }} /></div>}
      <form onSubmit={send} className="flex items-end gap-3 border-t border-gray-300 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <label className="min-w-0 flex-1"><span className="sr-only">Message</span>
          <textarea value={input} onChange={event => setInput(event.target.value)} maxLength={4000} rows={2}
            onKeyDown={event => {
              if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
                event.preventDefault(); send(event);
              }
            }}
            placeholder="Write a message…" className="w-full resize-none rounded-lg border bg-transparent p-3 focus:outline-blue-500" /></label>
        <button type="button" aria-label="Choose emoji" aria-expanded={showEmojiPicker} onClick={() => setShowEmojiPicker(value => !value)} className="rounded border px-3 py-3">☺</button>
        <button disabled={!input.trim()} className="rounded-lg bg-blue-700 px-5 py-3 text-white disabled:opacity-50">Send</button>
      </form>
    </div>
  </div>;
}
