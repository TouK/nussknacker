import HttpService from "./HttpService"
import User from "../common/models/User"
import api from "../api"

const OAUTH2_BACKEND = "OAuth2"
const UNAUTHORIZED_CODE = 401

export default {
  updateSettings(store) {
    HttpService.fetchSettings().then(settingsResponse => {
      store.dispatch({type: "UI_SETTINGS", settings: settingsResponse.data})

      api.interceptors.response.use(response => response, (error) => {
        const settings = settingsResponse.data

        if (error.response.status === UNAUTHORIZED_CODE && _.has(settings, "authentication.backend") && _.get(settings, "authentication.backend") === OAUTH2_BACKEND) {
          window.location.replace(_.get(settings, "authentication.url"))
        }

        return Promise.reject(error)
      })

      HttpService.fetchLoggedUser().then(userResponse => {
        store.dispatch({type: "LOGGED_USER", user: new User(userResponse.data)})
      })
    })
  }
}