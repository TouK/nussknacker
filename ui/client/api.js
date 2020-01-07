import {API_URL} from "./config"
import axios from "axios"
import SystemUtils from "./common/SystemUtils";

let headers = {}
if (SystemUtils.hasAccessToken()) {
  headers.authorization = SystemUtils.authorizationToken()
}

const configuration = {
  withCredentials: !!SystemUtils.hasAccessToken(),
  baseURL: API_URL,
  headers: headers
}

export default axios.create(configuration)
