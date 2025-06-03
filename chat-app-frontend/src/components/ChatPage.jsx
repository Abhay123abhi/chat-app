import React, { useEffect, useRef, useState } from "react";
import { MdAttachFile, MdSend, MdOutlineEmojiEmotions, MdLogout, MdDarkMode, MdLightMode } from "react-icons/md";
import { BsPinAngle } from "react-icons/bs";
import useChatContext from "../context/ChatContext";
import { useNavigate } from "react-router";
import SockJS from "sockjs-client";
import { Stomp } from "@stomp/stompjs";
import toast from "react-hot-toast";
import { baseURL } from "../config/AxiosHelper";
import { getMessagess } from "../services/RoomService";
import { timeAgo } from "../config/helper";
import EmojiPicker from "emoji-picker-react";

const ChatPage = () => {
  const {
    roomId,
    currentUser,
    connected,
    setConnected,
    setRoomId,
    setCurrentUser,
  } = useChatContext();

  const navigate = useNavigate();
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [pinnedMessage, setPinnedMessage] = useState(null);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const chatBoxRef = useRef(null);
  const [stompClient, setStompClient] = useState(null);
  const [theme, setTheme] = useState("light");

  useEffect(() => {
    if (!connected) {
      navigate("/");
    }
  }, [connected, roomId, currentUser]);

  useEffect(() => {
    async function loadMessages() {
      try {
        const messages = await getMessagess(roomId);
        setMessages(messages);
      } catch (error) {
        toast.error("Failed to load messages");
      }
    }
    if (connected) {
      loadMessages();
    }
  }, [connected, roomId]);

  useEffect(() => {
    if (chatBoxRef.current) {
      chatBoxRef.current.scroll({
        top: chatBoxRef.current.scrollHeight,
        behavior: "smooth",
      });
    }
  }, [messages]);

  useEffect(() => {
    const connectWebSocket = () => {
      const sock = new SockJS(`${baseURL}/chat`);
      const client = Stomp.over(sock);

      client.connect({}, () => {
        setStompClient(client);
        toast.success("Connected to chat");

        client.subscribe(`/topic/room/${roomId}`, (message) => {
          const newMessage = JSON.parse(message.body);
          setMessages((prev) => [...prev, newMessage]);
        });
      });
    };

    if (connected) {
      connectWebSocket();
    }
  }, [roomId, connected]);

  const sendMessage = () => {
    if (stompClient && connected && input.trim()) {
      const message = {
        sender: currentUser,
        content: input,
        roomId: roomId,
        timeStamp: new Date(),
      };

      stompClient.send(
        `/app/sendMessage/${roomId}`,
        {},
        JSON.stringify(message)
      );
      setInput("");
    }
  };

  const handleLogout = () => {
    stompClient.disconnect();
    setConnected(false);
    setRoomId("");
    setCurrentUser("");
    navigate("/");
  };

  const toggleTheme = () => {
    setTheme((prevTheme) => (prevTheme === "light" ? "dark" : "light"));
  };

  const handleFileUpload = (e) => {
    const file = e.target.files[0];
    if (file) {
      toast.success(`File "${file.name}" uploaded successfully!`);
    }
  };

  const handlePinMessage = (message) => {
    setPinnedMessage(message);
    toast.success("Message pinned!");
  };

  return (
    <div className={`min-h-screen ${theme === "dark" ? "bg-gray-900 text-white" : "bg-gray-100 text-black"}`}>
      {/* Header */}
      <header className="fixed w-full py-4 px-6 flex justify-between items-center shadow-md bg-gray-800 text-white">
        <div>
          <h1 className="text-lg font-bold">
            Room: <span className="text-blue-400">{roomId}</span>
          </h1>
        </div>
        <div>
          <h1 className="text-lg font-bold">
            User: <span className="text-green-400">{currentUser}</span>
          </h1>
        </div>
        <div className="flex items-center gap-4">
          <button onClick={toggleTheme} className="text-2xl">
            {theme === "light" ? <MdDarkMode /> : <MdLightMode />}
          </button>
          <button onClick={handleLogout} className="text-2xl text-red-400">
            <MdLogout />
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className="pt-20 px-4 flex flex-col h-screen">
        {/* Pinned Message */}
        {pinnedMessage && (
          <div className="bg-yellow-300 text-black p-3 rounded mb-4 shadow-md">
            <strong>Pinned:</strong> {pinnedMessage.content}
          </div>
        )}

        {/* Chat Messages */}
        <div
          ref={chatBoxRef}
          className="flex-grow p-4 border rounded-lg overflow-y-auto bg-gray-50 dark:bg-gray-800"
        >
          {messages.map((message, index) => (
            <div
              key={index}
              className={`flex ${
                message.sender === currentUser ? "justify-end" : "justify-start"
              } mb-4`}
            >
              <div
                className={`p-3 max-w-xs break-words shadow-md ${
                  message.sender === currentUser
                    ? "bg-blue-600 text-white rounded-l-lg rounded-tr-lg"
                    : "bg-gray-200 dark:bg-gray-700 dark:text-gray-200 rounded-r-lg rounded-tl-lg"
                }`}
              >
                <div className="flex items-center gap-2">
                  <strong>{message.sender}</strong>
                  <button
                    onClick={() => handlePinMessage(message)}
                    className="text-yellow-400"
                  >
                    <BsPinAngle />
                  </button>
                </div>
                <p className="mt-2">{message.content}</p>
                <span className="text-xs text-gray-400">
                  {timeAgo(message.timeStamp)}
                </span>
              </div>
            </div>
          ))}
        </div>

        {/* Input Section */}
        <div className="mt-4 flex items-center gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && sendMessage()}
            placeholder="Type your message..."
            className={`flex-1 px-4 py-2 border rounded-lg focus:outline-none ${
              theme === "dark" ? "bg-gray-700 text-white" : "bg-gray-200 text-black"
            }`}
          />
          <button
            onClick={() => setShowEmojiPicker(!showEmojiPicker)}
            className="text-2xl text-yellow-500"
          >
            <MdOutlineEmojiEmotions />
          </button>
          <input
            type="file"
            onChange={handleFileUpload}
            className="hidden"
            id="file-upload"
          />
          <label
            htmlFor="file-upload"
            className="text-2xl text-purple-500 cursor-pointer"
          >
            <MdAttachFile />
          </label>
          <button onClick={sendMessage} className="text-2xl text-green-500">
            <MdSend />
          </button>
        </div>

        {/* Emoji Picker */}
        {showEmojiPicker && (
          <div className="absolute bottom-20 left-4">
            <EmojiPicker
              onEmojiClick={(emojiObject) =>
                setInput((prev) => prev + emojiObject.emoji)
              }
            />
          </div>
        )}
      </main>
    </div>
  );
};

export default ChatPage;