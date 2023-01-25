/* eslint-disable i18next/no-literal-string */
import {AxiosError, AxiosResponse} from "axios"
import i18next from "i18next"
import {SettingsData} from "../../types/settings"
import api from "../api"
import {UserData} from "../common/models/User"
import {ProcessStateType, ProcessType} from "../components/Process/types"
import {AuthenticationSettings} from "../reducers/settings"

type HealthCheckProcessDeploymentType = {
  status: string,
  message: null | string,
  processes: null | Array<string>,
}

export type HealthCheckResponse = {
  state: HealthState,
  error?: string,
  processes?: string[],
}

export enum HealthState {
  ok = "ok",
  error = "error",
}

export type FetchProcessQueryParams = Partial<{
  search: string,
  categories: string,
  isSubprocess: boolean,
  isArchived: boolean,
  isDeployed: boolean,
}>

export type StatusesType = Record<ProcessType["name"], ProcessStateType>

type NotificationActions = {
  success(message: string): void,
  error(message: string, error: string, showErrorText: boolean): void,
}

class HttpService {

  //TODO: Move show information about error to another place. HttpService should avoid only action (get / post / etc..) - handling errors should be in another place.
  #notificationActions: NotificationActions = null

  fetchHealthCheckProcessDeployment(): Promise<HealthCheckResponse> {
    return api.get("/app/healthCheck/process/deployment")
      .then(() => ({state: HealthState.ok}))
      .catch((error) => {
        const {message, processes}: HealthCheckProcessDeploymentType = error.response?.data
        return {state: HealthState.error, error: message, processes: processes}
      })
  }

  #fetchSettings() {
    return api.get<SettingsData>("/settings")
  }

  fetchSettingsWithAuth(): Promise<SettingsData & { authentication: AuthenticationSettings }> {
    return this.#fetchSettings()
      .then(({data}) => {
        const {provider} = data.authentication
        const settings = data
        return this.fetchAuthenticationSettings(provider)
          .then(({data}) => {
            return {
              ...settings,
              authentication: {
                ...settings.authentication,
                ...data,
              },
            }
          })
      })
  }

  fetchLoggedUser() {
    return api.get<UserData>("/user")
  }

  fetchProcesses(data: FetchProcessQueryParams = {}): Promise<AxiosResponse<ProcessType[]>> {
    return api.get<ProcessType[]>("/processes", {params: data})
  }

  fetchProcessesStates() {
    return api.get<StatusesType>("/processes/status")
      .catch(error => Promise.reject(this.#addError(i18next.t("notification.error.cannotFetchStatuses", "Cannot fetch statuses"), error)))
  }

  fetchOAuth2AccessToken<T>(provider: string, authorizeCode: string | string[], redirectUri: string | null) {
    return api.get<T>(`/authentication/${provider.toLowerCase()}?code=${authorizeCode}${redirectUri ? `&redirect_uri=${redirectUri}` : ""}`)
  }

  fetchAuthenticationSettings(authenticationProvider: string) {
    return api.get<AuthenticationSettings>(`/authentication/${authenticationProvider.toLowerCase()}/settings`)
  }

  #addErrorMessage(message: string, error: string, showErrorText: boolean) {
    if (this.#notificationActions) {
      this.#notificationActions.error(message, error, showErrorText)
    }
  }

  async #addError(message: string, error?: AxiosError<unknown>, showErrorText = false) {
    console.warn(message, error)
    const errorResponseData = error?.response?.data || error.message
    const errorMessage = errorResponseData instanceof Blob ?
      await errorResponseData.text() :
      typeof errorResponseData === "string" ? errorResponseData : JSON.stringify(errorResponseData)
    this.#addErrorMessage(message, errorMessage, showErrorText)
    return Promise.resolve(error)
  }

}

export default new HttpService()

