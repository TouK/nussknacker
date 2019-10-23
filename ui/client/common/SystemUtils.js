const BEARER_CASE = 'Bearer'
const CACHE_TOKEN = "accessToken"

class SystemUtils {
  authorizationToken = () => {
    return `${BEARER_CASE} ${this.getAccessToken()}`
  }

  saveAccessToken = (token) => {
    localStorage.setItem(CACHE_TOKEN, token)
  }

  getAccessToken = () => localStorage.getItem(CACHE_TOKEN)

  hasAccessToken = () => this.getAccessToken() !== null

  removeAccessToken = () => {
    localStorage.removeItem(CACHE_TOKEN)
  }
}

export default new SystemUtils()