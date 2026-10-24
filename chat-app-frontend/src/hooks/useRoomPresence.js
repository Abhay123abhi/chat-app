import { useCallback, useEffect, useRef, useState } from "react";
import { getPresence } from "../services/RoomService";

export default function useRoomPresence({ roomId, currentUser, connected }) {
  const [members, setMembers] = useState([]);
  const activeRoom = useRef(roomId);
  activeRoom.current = roomId;

  useEffect(() => { setMembers([]); }, [roomId, currentUser, connected]);

  const acceptPresence = useCallback(snapshot => {
    if (snapshot?.roomId === activeRoom.current && Array.isArray(snapshot.members))
      setMembers(snapshot.members);
  }, []);

  const synchronizePresence = useCallback(async signal => {
    try {
      acceptPresence(await getPresence(roomId, signal));
    } catch (error) {
      if (error.code !== "ERR_CANCELED") {
        // Presence is optional; persisted messages continue without it.
      }
    }
  }, [roomId, acceptPresence]);

  const markDisconnected = useCallback(() => {
    setMembers(previous => previous.map(member =>
      member.name === currentUser ? { ...member, online: false } : member));
  }, [currentUser]);

  return { members, acceptPresence, synchronizePresence, markDisconnected };
}
