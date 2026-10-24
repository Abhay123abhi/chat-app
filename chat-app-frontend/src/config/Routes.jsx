import { Navigate, Route, Routes } from "react-router";
import JoinCreateChat from "../components/JoinCreateChat";
import ChatPage from "../components/ChatPage";

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<JoinCreateChat />} />
      <Route path="/chat" element={<ChatPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
