import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";

export default function useRoomConnection({
  roomId, currentUser, connected, accept, acceptPresence, badLiveUpdate,
  synchronize, synchronizePresence, markDisconnected,
}) {
  const [status, setStatus] = useState("Connecting");

  useEffect(() => {
    if (!connected || !roomId || !currentUser) return;
    let disposed = false;
    const abort = new AbortController();
    const client = new Client({
      brokerURL: `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/ws/chat`,
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
          catch { badLiveUpdate(); }
        });
        client.subscribe(`/topic/presence/${roomId}`, frame => {
          try { acceptPresence(JSON.parse(frame.body)); }
          catch { /* Presence is refreshed on reconnect. */ }
        });
        synchronizePresence(abort.signal);
        synchronize();
      },
      onWebSocketClose: () => {
        if (!disposed) {
          setStatus("Reconnecting");
          markDisconnected();
        }
      },
      onStompError: () => { if (!disposed) setStatus("Connection error"); },
    });
    client.activate();
    synchronize();
    const interval = setInterval(synchronize, 5000);
    return () => {
      disposed = true;
      abort.abort();
      clearInterval(interval);
      client.deactivate();
    };
  }, [
    roomId, currentUser, connected, accept, acceptPresence, badLiveUpdate,
    synchronize, synchronizePresence, markDisconnected,
  ]);

  return status;
}
