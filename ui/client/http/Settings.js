import HttpService from './HttpService'

export default {
  //@TODO: Replace jquery ajax by axios and send two requests asynchronously
  updateSettings(store) {
    HttpService.fetchSettings().then((response) => store.dispatch({type: "UI_SETTINGS", settings: response.data}))
    HttpService.fetchLoggedUser().then((user) => store.dispatch({type: "LOGGED_USER", user: user }))
  }
}