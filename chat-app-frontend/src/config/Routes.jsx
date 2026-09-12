import { Navigate, Route, Routes } from "react-router";
import App from "../App";
import ChatPage from "../components/ChatPage";

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<App />} />
      <Route path="/chat" element={<ChatPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
