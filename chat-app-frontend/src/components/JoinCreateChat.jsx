import React, { useState } from "react";
import chatIcon from "../assets/chat.png";
import { MdDarkMode, MdLightMode } from "react-icons/md"; // Icons for dark and light mode
import toast from "react-hot-toast";
import { createRoomApi, joinChatApi } from "../services/RoomService";
import useChatContext from "../context/ChatContext";
import { useNavigate } from "react-router";

const JoinCreateChat = () => {
  const [detail, setDetail] = useState({
    roomId: "",
    userName: "",
  });

  const { setRoomId, setCurrentUser, setConnected } = useChatContext();
  const navigate = useNavigate();
  const [theme, setTheme] = useState("light");

  function handleFormInputChange(event) {
    setDetail({
      ...detail,
      [event.target.name]: event.target.value,
    });
  }

  function validateForm() {
    if (detail.roomId === "" || detail.userName === "") {
      toast.error("Invalid Input !!");
      return false;
    }
    return true;
  }

  async function joinChat() {
    if (validateForm()) {
      try {
        const room = await joinChatApi(detail.roomId);
        toast.success("Joined successfully!");
        setCurrentUser(detail.userName);
        setRoomId(room.roomId);
        setConnected(true);
        navigate("/chat");
      } catch (error) {
        if (error.status === 400) {
          toast.error(error.response.data);
        } else {
          toast.error("Error in joining room");
        }
        console.error(error);
      }
    }
  }

  async function createRoom() {
    if (validateForm()) {
      try {
        const response = await createRoomApi(detail.roomId);
        toast.success("Room created successfully!");
        setCurrentUser(detail.userName);
        setRoomId(response.roomId);
        setConnected(true);
        navigate("/chat");
      } catch (error) {
        if (error.status === 400) {
          toast.error("Room already exists!");
        } else {
          toast.error("Error in creating room");
        }
        console.error(error);
      }
    }
  }

  const toggleTheme = () => {
    setTheme((prevTheme) => (prevTheme === "light" ? "dark" : "light"));
  };

  return (
    <div
      className={`min-h-screen flex items-center justify-center relative ${
        theme === "dark" ? "bg-gray-900 text-white" : "bg-gray-100 text-black"
      }`}
    >
      {/* Dark Mode Toggle in Corner */}
      <button
        onClick={toggleTheme}
        className={`absolute top-4 right-4 p-2 rounded-full shadow-lg ${
          theme === "dark" ? "bg-blue-500 text-white" : "bg-gray-300 text-black"
        }`}
      >
        {theme === "light" ? <MdDarkMode size={24} /> : <MdLightMode size={24} />}
      </button>

      {/* Main Card */}
      <div
        className={`p-8 border w-full flex flex-col gap-6 max-w-md rounded-lg shadow-lg ${
          theme === "dark" ? "bg-gray-800 border-gray-700" : "bg-white border-gray-300"
        }`}
      >
        {/* Header */}
        <div className="flex justify-center items-center">
          <img src={chatIcon} alt="Chat Icon" className="w-16" />
        </div>

        <h1 className="text-2xl font-bold text-center">
          Join or Create a Room
        </h1>

        {/* Name Input */}
        <div>
          <label htmlFor="userName" className="block font-medium mb-2">
            Your Name
          </label>
          <input
            onChange={handleFormInputChange}
            value={detail.userName}
            type="text"
            id="userName"
            name="userName"
            placeholder="Enter your name"
            className={`w-full px-4 py-2 rounded-lg focus:outline-none ${
              theme === "dark"
                ? "bg-gray-700 border-gray-600 text-white focus:ring-blue-500"
                : "bg-gray-200 border-gray-300 text-black focus:ring-blue-500"
            }`}
          />
        </div>

        {/* Room ID Input */}
        <div>
          <label htmlFor="roomId" className="block font-medium mb-2">
            Room ID / New Room ID
          </label>
          <input
            onChange={handleFormInputChange}
            value={detail.roomId}
            type="text"
            id="roomId"
            name="roomId"
            placeholder="Enter the room ID"
            className={`w-full px-4 py-2 rounded-lg focus:outline-none ${
              theme === "dark"
                ? "bg-gray-700 border-gray-600 text-white focus:ring-blue-500"
                : "bg-gray-200 border-gray-300 text-black focus:ring-blue-500"
            }`}
          />
        </div>

        {/* Buttons */}
        <div className="flex justify-between gap-4">
          <button
            onClick={joinChat}
            className={`w-full px-4 py-2 rounded-lg font-medium ${
              theme === "dark"
                ? "bg-blue-500 hover:bg-blue-600 text-white"
                : "bg-blue-400 hover:bg-blue-500 text-white"
            }`}
          >
            Join Room
          </button>
          <button
            onClick={createRoom}
            className={`w-full px-4 py-2 rounded-lg font-medium ${
              theme === "dark"
                ? "bg-orange-500 hover:bg-orange-600 text-white"
                : "bg-orange-400 hover:bg-orange-500 text-white"
            }`}
          >
            Create Room
          </button>
        </div>
      </div>
    </div>
  );
};

export default JoinCreateChat;