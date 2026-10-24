import { useContext } from "react";
import { ChatContext } from "./chatContext";

export default function useChatContext() {
  const context = useContext(ChatContext);
  if (!context) throw new Error("useChatContext must be used within ChatProvider");
  return context;
}
