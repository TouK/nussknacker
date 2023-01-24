/* eslint-disable i18next/no-literal-string */
import {AxiosError, AxiosResponse} from "axios"
import FileSaver from "file-saver"
import i18next from "i18next"
import {Moment} from "moment"
import {SettingsData} from "../actions/nk"
import api from "../api"
import {UserData} from "../common/models/User"
import {ProcessActionType, ProcessStateType, ProcessType} from "../components/Process/types"
import {AuthenticationSettings} from "../reducers/settings"
import {ProcessDefinitionData} from "../types"
import {Instant} from "../types/common"
import {BackendNotification} from "../containers/Notifications"
import {ProcessCounts} from "../reducers/graph"
import {TestResults} from "../common/TestResultUtils"

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

export interface AppBuildInfo {
  name: string,
  gitCommit: string,
  buildTime: string,
  version: string,
  processingType: any,
}

type Services = Record<string, Record<string, {
  "parameters": unknown[],
  "returnType": unknown,
  "categories": string[],
  "nodeConfig": unknown,
}>>

export type ComponentActionType = {
  id: string,
  title: string,
  icon: string,
  url?: string,
}

export type ComponentType = {
  id: string,
  name: string,
  icon: string,
  componentType: string,
  componentGroupName: string,
  categories: string[],
  actions: ComponentActionType[],
  usageCount: number,
  links: Array<{
    id: string,
    title: string,
    icon: string,
    url: string,
  }>,
}

export type ComponentUsageType = {
  id: string,
  name: string,
  processId: string,
  nodesId: string[],
  isArchived: boolean,
  isSubprocess: boolean,
  processCategory: string,
  modificationDate: Instant,
  modifiedBy: string,
  createdAt: Instant,
  createdBy: string,
  lastAction: ProcessActionType,
}

type NotificationActions = {
  success(message: string): void,
  error(message: string, error: string, showErrorText: boolean): void,
}

export interface TestProcessResponse {
  results: TestResults,
  counts: ProcessCounts,
}

class HttpService {

  //TODO: Move show information about error to another place. HttpService should avoid only action (get / post / etc..) - handling errors should be in another place.
  #notificationActions: NotificationActions = null

  setNotificationActions(na: NotificationActions) {
    this.#notificationActions = na
  }

  loadBackendNotifications(): Promise<BackendNotification[]> {
    return api.get<BackendNotification[]>("/notifications")
      .then(d => d.data)
      .catch(error => {
        this.#addError(i18next.t("notification.error.cannotFetchBackendNotifications", "Cannot fetch backend notification"), error)
        return []
      })
  }

  fetchHealthCheckProcessDeployment(): Promise<HealthCheckResponse> {
    return api.get("/app/healthCheck/process/deployment")
      .then(() => ({state: HealthState.ok}))
      .catch((error) => {
        const {message, processes}: HealthCheckProcessDeploymentType = error.response?.data
        return {state: HealthState.error, error: message, processes: processes}
      })
  }

  fetchSettings() {
    return api.get<SettingsData>("/settings")
  }

  fetchSettingsWithAuth(): Promise<SettingsData & { authentication: AuthenticationSettings }> {
    return this.fetchSettings()
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

  fetchAppBuildInfo() {
    return api.get<AppBuildInfo>("/app/buildInfo")
  }

  fetchProcessDefinitionData(processingType: string, isSubprocess: boolean) {
    const promise = api.get<ProcessDefinitionData>(`/processDefinitionData/${processingType}?isSubprocess=${isSubprocess}`)
      .then(response => {
        // This is a walk-around for having part of node template (branch parameters) outside of itself.
        // See note in DefinitionPreparer on backend side. // TODO remove it after API refactor
        response.data.componentGroups.forEach(group => {
          group.components.forEach(component => {
            component.node.branchParametersTemplate = component.branchParametersTemplate
          })
        })

        return response
      })
    promise.catch((error) => this.#addError(i18next.t("notification.error.cannotFindChosenVersions", "Cannot find chosen versions"), error, true))
    return promise
  }

  fetchDictLabelSuggestions(processingType, dictId, labelPattern) {
    return api.get(`/processDefinitionData/${processingType}/dict/${dictId}/entry?label=${labelPattern}`)
  }

  fetchProcesses(data: FetchProcessQueryParams = {}): Promise<AxiosResponse<ProcessType[]>> {
    return api.get<ProcessType[]>("/processes", {params: data})
  }

  fetchProcessDetails(processId: string, versionId?: string) {
    const id = encodeURIComponent(processId)
    const url = versionId ? `/processes/${id}/${versionId}` : `/processes/${id}`
    return api.get(url)
  }

  fetchProcessesStates() {
    return api.get<StatusesType>("/processes/status")
      .catch(error => Promise.reject(this.#addError(i18next.t("notification.error.cannotFetchStatuses", "Cannot fetch statuses"), error)))
  }

  fetchProcessState(processId) {
    return api.get(`/processes/${encodeURIComponent(processId)}/status`)
      .catch(error => this.#addError(i18next.t("notification.error.cannotFetchStatus", "Cannot fetch status"), error))
  }

  fetchProcessesDeployments(processId: string) {
    return api.get<{ performedAt: string, action: "UNARCHIVE" | "ARCHIVE" | "CANCEL" | "DEPLOY" }[]>(`/processes/${encodeURIComponent(processId)}/deployments`)
      .then(res => res.data
        .filter(({action}) => action === "DEPLOY")
        .map(({performedAt}) => performedAt))
  }

  customAction(processId: string, actionName: string, params: Record<string, unknown>) {
    const data = {actionName: actionName, params: params}
    return api.post(`/processManagement/customAction/${encodeURIComponent(processId)}`, data).then(res => {
      this.#addInfo(res.data.msg)
      return {isSuccess: res.data.isSuccess}
    }).catch(error => {
      const msg = error.response.data.msg || error.response.data
      return this.#addError(msg, error, false).then(() => {
        return {isSuccess: false}
      })
    })
  }

  cancel(processId, comment?) {
    return api.post(`/processManagement/cancel/${encodeURIComponent(processId)}`, comment)
      .catch(error => {
        if (error?.response?.status != 400) {
          return this.#addError(i18next.t("notification.error.failedToCancel", "Failed to cancel {{processId}}", {processId}), error, true)
            .then((error) => {
              return {isSuccess: false}
            })
        } else {
          throw error
        }
      })
  }

  fetchProcessActivity(processId) {
    return api.get(`/processes/${encodeURIComponent(processId)}/activity`)
  }

  addComment(processId, versionId, data) {
    return api.post(`/processes/${encodeURIComponent(processId)}/${versionId}/activity/comments`, data)
      .then(() => this.#addInfo(i18next.t("notification.info.commentAdded", "Comment added")))
      .catch(error => this.#addError(i18next.t("notification.error.failedToAddComment", "Failed to add comment"), error))
  }

  deleteComment(processId, commentId) {
    return api.delete(`/processes/${encodeURIComponent(processId)}/activity/comments/${commentId}`)
      .then(() => this.#addInfo(i18next.t("notification.info.commendDeleted", "Comment deleted")))
      .catch(error => this.#addError(i18next.t("notification.error.failedToDeleteComment", "Failed to delete comment"), error))
  }

  addAttachment(processId, versionId, file) {
    const data = new FormData()
    data.append("attachment", file)

    return api.post(`/processes/${encodeURIComponent(processId)}/${versionId}/activity/attachments`, data)
      .then(() => this.#addInfo(i18next.t("notification.error.attachmentAdded", "Attachment added")))
      .catch(error => this.#addError(i18next.t("notification.error.failedToAddAttachment", "Failed to add attachment"), error, true))
  }

  downloadAttachment(processId, processVersionId, attachmentId, fileName) {
    return api.get(`/processes/${encodeURIComponent(processId)}/${processVersionId}/activity/attachments/${attachmentId}`, {responseType: "blob"})
      .then(response => FileSaver.saveAs(response.data, fileName))
      .catch(error => this.#addError(i18next.t("notification.error.failedToDownloadAttachment", "Failed to download attachment"), error))
  }

  changeProcessName(processName, newProcessName): Promise<boolean> {
    const failedToChangeNameMessage = i18next.t("notification.error.failedToChangeName", "Failed to change scenario name:")
    if (newProcessName == null || newProcessName === "") {
      this.#addErrorMessage(failedToChangeNameMessage, i18next.t("notification.error.newNameEmpty", "Name cannot be empty"), true)
      return Promise.resolve(false)
    }

    return api.put(`/processes/${encodeURIComponent(processName)}/rename/${encodeURIComponent(newProcessName)}`)
      .then(() => {
        this.#addInfo(i18next.t("notification.error.nameChanged", "Scenario name changed"))
        return true
      })
      .catch((error) => {
        return this.#addError(failedToChangeNameMessage, error, true).then(() => false)
      })
  }

  exportProcessToPdf(processId, versionId, data) {
    return api.post(`/processesExport/pdf/${encodeURIComponent(processId)}/${versionId}`, data, {responseType: "blob"})
      .then(response => FileSaver.saveAs(response.data, `${processId}-${versionId}.pdf`))
      .catch(error => this.#addError(i18next.t("notification.error.failedToExportPdf", "Failed to export PDF"), error))
  }

  fetchProcessCounts(processId: string, dateFrom: Moment, dateTo: Moment): Promise<AxiosResponse<ProcessCounts>> {
    //we use offset date time instead of timestamp to pass info about user time zone to BE
    const format = (date: Moment) => date?.format("YYYY-MM-DDTHH:mm:ssZ")

    const data = {dateFrom: format(dateFrom), dateTo: format(dateTo)}
    const promise = api.get(`/processCounts/${encodeURIComponent(processId)}`, {params: data})

    promise.catch(error => this.#addError(i18next.t("notification.error.failedToFetchCounts", "Cannot fetch process counts"), error, true))
    return promise
  }

  fetchOAuth2AccessToken<T>(provider: string, authorizeCode: string | string[], redirectUri: string | null) {
    return api.get<T>(`/authentication/${provider.toLowerCase()}?code=${authorizeCode}${redirectUri ? `&redirect_uri=${redirectUri}` : ""}`)
  }

  fetchAuthenticationSettings(authenticationProvider: string) {
    return api.get<AuthenticationSettings>(`/authentication/${authenticationProvider.toLowerCase()}/settings`)
  }

  #addInfo(message: string) {
    if (this.#notificationActions) {
      this.#notificationActions.success(message)
    }
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

