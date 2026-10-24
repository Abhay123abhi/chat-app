import { useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import useChatContext from "../context/useChatContext";
import useRoomMessages from "../hooks/useRoomMessages";
import useRoomPresence from "../hooks/useRoomPresence";
import useRoomConnection from "../hooks/useRoomConnection";
import ChatSidebar from "./ChatSidebar";
import ConversationPanel from "./ConversationPanel";

// A room-specific component keeps message, presence and connection state together.
// React remounts it when the guest or room changes, so hooks start with fresh state.
function ActiveChatRoom({ roomId, currentUser }) {
  const { setConnected, setRoomId, setCurrentUser } = useChatContext();
  const navigate = useNavigate();
  const box = useRef(null);
  const follow = useRef(true);

  const history = useRoomMessages({ roomId, currentUser, connected: true, box, follow });
  const presence = useRoomPresence({ roomId, currentUser });
  const status = useRoomConnection({
    roomId, currentUser, connected: true,
    accept: history.accept, badLiveUpdate: history.badLiveUpdate,
    synchronize: history.synchronize, acceptPresence: presence.acceptPresence,
    synchronizePresence: presence.synchronizePresence, markDisconnected: presence.markDisconnected,
  });

  const visibleMembers = presence.members.length
    ? presence.members
    : [{ name: currentUser, online: status === "Live", lastSeen: null }];
  const onlineMembers = visibleMembers.filter(member => member.online);
  const offlineMembers = visibleMembers.filter(member => !member.online);
  const onlineCount = onlineMembers.length;

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
      <ConversationPanel roomId={roomId} currentUser={currentUser} status={status} onlineCount={onlineCount}
        visibleMembers={visibleMembers} box={box} follow={follow}
        {...history} />
    </main>
  );
}

export default function ChatPage() {
  const { roomId, currentUser, connected } = useChatContext();
  const navigate = useNavigate();

  useEffect(() => {
    if (!connected || !roomId || !currentUser) navigate("/", { replace: true });
  }, [connected, roomId, currentUser, navigate]);

  if (!connected || !roomId || !currentUser) return null;
  return <ActiveChatRoom key={JSON.stringify([roomId, currentUser])}
    roomId={roomId} currentUser={currentUser} />;
}
