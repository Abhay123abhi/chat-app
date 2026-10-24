import { useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import useChatContext from "../context/useChatContext";
import useRoomMessages from "../hooks/useRoomMessages";
import useRoomPresence from "../hooks/useRoomPresence";
import useRoomConnection from "../hooks/useRoomConnection";
import ChatSidebar from "./ChatSidebar";
import ConversationPanel from "./ConversationPanel";

export default function ChatPage() {
  const { roomId, currentUser, connected, setConnected, setRoomId, setCurrentUser } = useChatContext();
  const navigate = useNavigate();
  const box = useRef(null);
  const follow = useRef(true);

  const history = useRoomMessages({ roomId, currentUser, connected, box, follow });
  const presence = useRoomPresence({ roomId, currentUser, connected });
  const status = useRoomConnection({
    roomId, currentUser, connected,
    accept: history.accept, badLiveUpdate: history.badLiveUpdate,
    synchronize: history.synchronize, acceptPresence: presence.acceptPresence,
    synchronizePresence: presence.synchronizePresence, markDisconnected: presence.markDisconnected,
  });

  const visibleMembers = presence.members.length
    ? presence.members
    : currentUser ? [{ name: currentUser, online: status === "Live", lastSeen: null }] : [];
  const onlineMembers = visibleMembers.filter(member => member.online);
  const offlineMembers = visibleMembers.filter(member => !member.online);
  const onlineCount = onlineMembers.length;

  useEffect(() => {
    if (!connected || !roomId || !currentUser) navigate("/", { replace: true });
  }, [connected, roomId, currentUser, navigate]);

  function leave() {
    if (history.pending.length && !window.confirm("Unsent messages will be lost. Leave this room?")) return;
    setConnected(false);
    setRoomId("");
    setCurrentUser("");
    navigate("/");
  }

  return (
    <main className="chat-workspace">
      <ChatSidebar roomId={roomId} currentUser={currentUser} status={status}
        onlineMembers={onlineMembers} offlineMembers={offlineMembers}
        onlineCount={onlineCount} leave={leave} />
      <ConversationPanel roomId={roomId} currentUser={currentUser} onlineCount={onlineCount}
        visibleMembers={visibleMembers} box={box} follow={follow}
        {...history} />
    </main>
  );
}
