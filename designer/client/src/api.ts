import axios from "axios"
import SystemUtils, {AUTHORIZATION_HEADER_NAMESPACE} from "./common/SystemUtils"
import {API_URL} from "./config"

const headers = {}
if (SystemUtils.hasAccessToken()) {
  headers[AUTHORIZATION_HEADER_NAMESPACE] = SystemUtils.authorizationToken()
}

const configuration = {
  withCredentials: true,
  baseURL: API_URL,
  headers: headers,
}

export default axios.create(configuration)
