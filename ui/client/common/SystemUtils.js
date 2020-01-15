import {v4 as uuid4} from "uuid"
import api from "../api"

const ACCESS_TOKEN_NAMESPACE = "accessToken"
const BEARER_CASE = "Bearer"
const userId = "userId"

class SystemUtils {
  authorizationToken = () => {
    return `${BEARER_CASE} ${this.getAccessToken()}`
  }

  saveAccessToken = (token) => {
    localStorage.setItem(ACCESS_TOKEN_NAMESPACE, token)
  }

  getAccessToken = () => localStorage.getItem(ACCESS_TOKEN_NAMESPACE)

  hasAccessToken = () => this.getAccessToken() !== null

  removeAccessToken = () => {
    localStorage.removeItem(ACCESS_TOKEN_NAMESPACE)
  }

  clearAuthorizationToken = () => {
    this.removeAccessToken()

    api.interceptors.request.use(function (config) {
      delete config["headers"]["Authorization"]
      return config
    })
  }

  setAuthorizationToken = (token) => {
    const self = this
    this.saveAccessToken(token)

    api.interceptors.request.use(function (config) {
      config["headers"]["Authorization"] = self.authorizationToken()
      return config
    })
  }

  getUserId() {
    if (_.isEmpty(localStorage.getItem(userId))) {
      localStorage.setItem(userId, uuid4())
    }
    return localStorage.getItem(userId)
  }
}

export default new SystemUtils()
