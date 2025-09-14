import axios from "axios";

import { API_URL } from "./config";

const headers = {};

export default axios.create({
    withCredentials: true,
    baseURL: API_URL,
    headers,
});
