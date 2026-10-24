import { useCallback, useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import { getMessages, sendMessageApi } from "../services/RoomService";
import { mergeMessages } from "../services/messageState";

export default function useRoomMessages({ roomId, currentUser, connected, box, follow }) {
  const [messages, setMessages] = useState([]);
  const [pending, setPending] = useState([]);
  const [input, setInput] = useState("");
  const [historyError, setHistoryError] = useState("");
  const [hasOlder, setHasOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [newMessages, setNewMessages] = useState(false);
  const mounted = useRef(true);
  const activeRoom = useRef(roomId);
  const syncing = useRef(false);
  const syncNow = useRef(() => {});
  const session = useRef({ disposed: true, busy: false, cursor: null, abort: null });

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    if (!connected || !roomId || !currentUser) return;
    activeRoom.current = roomId;
    const current = { disposed: false, busy: false, cursor: null, abort: new AbortController() };
    session.current = current;
    setMessages([]);
    setPending([]);
    setHistoryError("");
    setHasOlder(false);
    setLoadingOlder(false);
    setNewMessages(false);
    follow.current = true;
    return () => {
      current.disposed = true;
      current.abort.abort();
      if (session.current === current) session.current = { disposed: true, busy: false, cursor: null, abort: null };
      syncing.current = false;
    };
  }, [roomId, currentUser, connected, follow]);

  const accept = useCallback((items) => {
    if (session.current.disposed) return;
    setMessages(previous => mergeMessages(previous, items));
    const received = new Set(items.map(message => message.clientMessageId));
    setPending(previous => previous.filter(item => !received.has(item.clientMessageId)));
    if (items.length && !follow.current) setNewMessages(true);
  }, [follow]);

  const synchronize = useCallback(async () => {
    const current = session.current;
    if (current.disposed || current.busy) return;
    current.busy = true;
    syncing.current = true;
    try {
      if (current.cursor === null) {
        const page = await getMessages(roomId, { limit: 50 }, current.abort.signal);
        if (current.disposed) return;
        accept(page.messages);
        setHasOlder(page.hasMore);
        current.cursor = page.messages.at(-1)?.sequence ?? 0;
      }
      for (let pageNumber = 0; pageNumber < 20 && !current.disposed; pageNumber++) {
        const page = await getMessages(roomId, { after: current.cursor, limit: 100 }, current.abort.signal);
        if (current.disposed) return;
        accept(page.messages);
        if (page.nextCursor !== null) current.cursor = page.nextCursor;
        if (!page.hasMore) break;
      }
      if (!current.disposed) setHistoryError("");
    } catch (error) {
      if (!current.disposed && error.code !== "ERR_CANCELED")
        setHistoryError("History is unavailable. Retrying automatically.");
    } finally {
      current.busy = false;
      if (session.current === current) syncing.current = false;
    }
  }, [roomId, accept]);

  useEffect(() => {
    syncNow.current = synchronize;
    return () => { if (syncNow.current === synchronize) syncNow.current = () => {}; };
  }, [synchronize]);

  const badLiveUpdate = useCallback(() => {
    setHistoryError("A live update could not be read. History will recover it.");
  }, []);

  const transmit = useCallback(async (item) => {
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
  }, [roomId]);

  function send(event) {
    event.preventDefault();
    if (!input.trim() || input.length > 4000) return;
    const item = {
      clientMessageId: crypto.randomUUID(), content: input.trim(),
      sender: currentUser, status: "Sending",
    };
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
      const oldHeight = box.current?.scrollHeight ?? 0;
      const oldTop = box.current?.scrollTop ?? 0;
      setMessages(previous => mergeMessages(previous, page.messages));
      setHasOlder(page.hasMore);
      requestAnimationFrame(() => {
        if (mounted.current && activeRoom.current === targetRoom && box.current)
          box.current.scrollTop = oldTop + box.current.scrollHeight - oldHeight;
      });
    } catch {
      toast.error("Could not load older messages");
    } finally {
      if (mounted.current && activeRoom.current === targetRoom) setLoadingOlder(false);
    }
  }

  useEffect(() => {
    if (follow.current && box.current) box.current.scrollTop = box.current.scrollHeight;
  }, [messages, pending, box, follow]);

  return {
    messages, pending, input, setInput, historyError, hasOlder, loadingOlder,
    newMessages, setNewMessages, accept, badLiveUpdate, synchronize, transmit, send, loadOlder,
  };
}
