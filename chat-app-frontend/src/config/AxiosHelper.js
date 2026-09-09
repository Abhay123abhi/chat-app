import axios from "axios";

export const baseURL = "";
export const httpClient = axios.create({ baseURL, timeout: 15000 });
