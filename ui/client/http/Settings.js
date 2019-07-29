import HttpService from "./HttpService"
import User from "../common/models/User"
import axios from "axios"

export default {
  updateSettings(store) {
    axios.all([HttpService.fetchSettings(), HttpService.fetchLoggedUser()]).then(axios.spread( (settingsResponse, userResponse) => {
      store.dispatch({type: "UI_SETTINGS", settings: settingsResponse.data})
      store.dispatch({type: "LOGGED_USER", user: new User(userResponse.data)})
    }))
  }
}