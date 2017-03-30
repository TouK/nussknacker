import HttpService from './HttpService'

export default {

  updateSettings(store) {
    HttpService.fetchLoggedUser().then((user) => store.dispatch({type: "LOGGED_USER", user: user }))
    HttpService.fetchGrafanaSettings().then((grafana) => store.dispatch({type: "GRAFANA_SETTINGS", grafanaSettings: grafana}))
    HttpService.fetchKibanaSettings().then((grafana) => store.dispatch({type: "KIBANA_SETTINGS", kibanaSettings: grafana}))
    HttpService.fetchMigrationSettings().then((migration) => store.dispatch({type: "MIGRATION_SETTINGS", migrationSettings: migration}))

  }

}