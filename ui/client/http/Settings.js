import HttpService from './HttpService'

export default {

  updateSettings(store) {
    HttpService.fetchLoggedUser().then((user) => store.dispatch({type: "LOGGED_USER", user: user }))

    HttpService.fetchSettings().then((settings) => {
      store.dispatch({type: "UI_SETTINGS", settings: settings})
    })
  }

}
