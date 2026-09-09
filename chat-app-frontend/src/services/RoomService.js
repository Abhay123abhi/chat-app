import { httpClient } from "../config/AxiosHelper";

export const createRoomApi = async (roomId) =>
  (await httpClient.post("/api/v1/rooms", roomId, { headers: { "Content-Type": "text/plain" } })).data;
export const joinChatApi = async (roomId) =>
  (await httpClient.get(`/api/v1/rooms/${encodeURIComponent(roomId)}`)).data;
export const getMessages = async (roomId, params = {}, signal) =>
  (await httpClient.get(`/api/v1/rooms/${encodeURIComponent(roomId)}/messages`, { params, signal })).data;
export const sendMessageApi = async (roomId, message) =>
  (await httpClient.post(`/api/v1/rooms/${encodeURIComponent(roomId)}/messages`, message)).data;
