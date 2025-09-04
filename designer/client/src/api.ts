import axios from "axios";

import { API_URL } from "./config";

const headers = {};

export function setToken(name: string, token: string) {
    headers[name] = token;
}

export default axios.create({
    withCredentials: true,
    baseURL: API_URL,
    headers,
});
