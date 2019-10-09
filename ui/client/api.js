import {API_URL} from "./config"
import axios from 'axios'

let accessToken = localStorage.getItem("accessToken")

let headers = {}
if (accessToken) {
  headers["Authorization"] = "Bearer " + accessToken
}

const configuration = {
  withCredentials: accessToken == null,
  baseURL: API_URL,
  headers: headers
}

export default axios.create(configuration)