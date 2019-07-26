import HttpService from './HttpService'

export default {
  //@TODO: Replace jquery ajax by axios and send two requests asynchronously
  updateSettings(store) {
    HttpService.fetchSettings().then((settings) => store.dispatch({type: "UI_SETTINGS", settings: settings}))
    HttpService.fetchLoggedUser().then((user) => store.dispatch({type: "LOGGED_USER", user: user }))
  }
}