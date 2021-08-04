/* eslint-disable i18next/no-literal-string */
import {AxiosError} from "axios"
import FileSaver from "file-saver"
import {SettingsData} from "../actions/nk"
import api from "../api"
import {UserData} from "../common/models/User"
import {ProcessStateType, ProcessType} from "../components/Process/types"
import {API_URL} from "../config"
import {AuthenticationSettings} from "../reducers/settings"
import {WithId} from "../types/common"
import {ToolbarsConfig} from "../components/toolbarSettings/types"
import i18next from "i18next"
import { Moment } from "moment"

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

//TODO: Move show information about error to another place. HttpService should avoid only action (get / post / etc..) - handling errors should be in another place.
class HttpService {
  notificationActions = null
  notificationReload = null

  setNotificationActions(na) {
    this.notificationActions = na
    if (this.notificationReload) {
      clearInterval(this.notificationReload)
    }
    //TODO: configuration?
    this.notificationReload = setInterval(() => this._loadNotifications(), 10000)
  }

  _loadNotifications() {
    api.get("/notifications").then(response => response.data.forEach(notification => {
      notification.type === "info" ? this.addInfo(notification.message) : this.addError(notification.message)
    }))
  }

  addInfo(message: string) {
    if (this.notificationActions) {
      this.notificationActions.success(message)
    }
  }

  addErrorMessage(message: string, error: any, showErrorText: boolean) {
    if (this.notificationActions) {
      this.notificationActions.error(message, error, showErrorText)
    }
  }

  addError<T>(message: string, error?: AxiosError<T>, showErrorText = false) {
    console.warn(message, error)
    const errorMessage = error?.response?.data || error.message
    this.addErrorMessage(message, errorMessage, showErrorText)
    return Promise.resolve(error)
  }

  availableQueryableStates() {
    return api.get("/queryableState/list")
  }

  queryState(processId, queryName, key) {
    const data = {processId, queryName, key}
    return api.get("/queryableState/fetch", {params: data})
      .catch(error => this.addError(i18next.t("notification.error.cannotFetchState", "Cannot fetch state"), error))
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

  fetchSettingsWithAuth() {
    return this.fetchSettings()
      .then(({data}) => {
        const {provider} = data.authentication
        const settings = data
        return this.fetchAuthenticationSettings<AuthenticationSettings>(provider)
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

  fetchProcessDefinitionData(processingType, isSubprocess) {
    const promise = api.get(`/processDefinitionData/${processingType}?isSubprocess=${isSubprocess}`)
      .then(response => {
        // This is a walk-around for having part of node template (branch parameters) outside of itself.
        // See note in DefinitionPreparer on backend side. // TODO remove it after API refactor
        response.data.nodesToAdd.forEach(nodeAggregates => {
          nodeAggregates.possibleNodes.forEach(nodeToAdd => {
            nodeToAdd.node.branchParametersTemplate = nodeToAdd.branchParametersTemplate
          })
        })

        return response
      })
    promise.catch((error) => this.addError(i18next.t("notification.error.cannotFindChosenVersions", "Cannot find chosen versions"), error, true))
    return promise
  }

  fetchComponentIds() {
    return api.get("/processDefinitionData/componentIds")
  }

  fetchServices() {
    return api.get("/processDefinitionData/services")
  }

  fetchDictLabelSuggestions(processingType, dictId, labelPattern) {
    return api.get(`/processDefinitionData/${processingType}/dict/${dictId}/entry?label=${labelPattern}`)
  }

  fetchUnusedComponents() {
    return api.get("/app/unusedComponents")
  }

  fetchProcessesComponents(componentId) {
    return api.get(`/processesComponents/${encodeURIComponent(componentId)}`)
  }

  fetchProcesses(data: FetchProcessQueryParams = {}) {
    return api.get<ProcessType[]>("/processes", {params: data})
  }

  fetchCustomProcesses() {
    return api.get<ProcessType[]>("/customProcesses")
  }

  fetchProcessDetails(processId, versionId?, businessView?) {
    const url = versionId ? `/processes/${processId}/${versionId}` : `/processes/${processId}`
    return api.get(url, {params: {businessView}})
  }

  fetchProcessesStates() {
    return api.get<StatusesType>("/processes/status")
      .catch(error => Promise.reject(this.addError(i18next.t("notification.error.cannotFetchStatuses", "Cannot fetch statuses"), error)))
  }

  fetchProcessToolbarsConfiguration(processId) {
    const promise = api.get<WithId<ToolbarsConfig>>(`/processes/${processId}/toolbars`)
    promise.catch(error => this.addError(i18next.t("notification.error.cannotFetchToolbarConfiguration", "Cannot fetch toolbars configuration"), error))
    return promise
  }

  fetchProcessState(processId) {
    return api.get(`/processes/${processId}/status`)
      .catch(error => this.addError(i18next.t("notification.error.cannotFetchStatus", "Cannot fetch status"), error))
  }

  fetchProcessesDeployments(processId: string) {
    return api.get<{performedAt: string, action: "UNARCHIVE" | "ARCHIVE" | "CANCEL" | "DEPLOY"}[]>(`/processes/${processId}/deployments`)
      .then(res => res.data
        .filter(({action}) => action === "DEPLOY")
        .map(({performedAt}) => performedAt))
  }

  deploy(processId, comment?) {
    return api.post(`/processManagement/deploy/${processId}`, comment).then(() => {
      this.addInfo(i18next.t("notification.info.scenarioDeployed", "Scenario {{processId}} was deployed", processId))
      return {isSuccess: true}
    }).catch(error => {
      return this.addError(i18next.t("notification.error.failedToDeploy", "Failed to deploy {{processId}}", processId), error, true).then(() => {
        return {isSuccess: false}
      })
    })
  }

  customAction(processId: string, actionName: string, params: Record<string, unknown>) {
    const data = {actionName: actionName, params: params}
    return api.post(`/processManagement/customAction/${processId}`, data).then(res => {
      this.addInfo(res.data.msg)
      return {isSuccess: res.data.isSuccess}
    }).catch(error => {
      const msg = error.response.data.msg || error.response.data
      return this.addError(msg, error, false).then(() => {
        return {isSuccess: false}
      })
    })
  }

  invokeService(processingType, serviceName, parameters) {
    return api.post(`/service/${processingType}/${serviceName}`, parameters)
  }

  cancel(processId, comment?) {
    return api.post(`/processManagement/cancel/${processId}`, comment)
      .then(() => this.addInfo(i18next.t("notification.info.scenarioCancelled", "Process {{processId}} was canceled", processId)))
      .catch(error => this.addError(i18next.t("notification.error.failedToCancel", "Failed to cancel {{processId}}", processId), error, true))
  }

  fetchProcessActivity(processId) {
    return api.get(`/processes/${processId}/activity`)
  }

  addComment(processId, versionId, data) {
    return api.post(`/processes/${processId}/${versionId}/activity/comments`, data)
      .then(() => this.addInfo(i18next.t("notification.info.commentAdded", "Comment added")))
      .catch(error => this.addError(i18next.t("notification.error.failedToAddComment", "Failed to add comment", error)))
  }

  deleteComment(processId, commentId) {
    return api.delete(`/processes/${processId}/activity/comments/${commentId}`)
      .then(() => this.addInfo(i18next.t("notification.info.commendDeleted", "Comment deleted")))
      .catch(error => this.addError(i18next.t("notification.error.failedToDeleteComment", "Failed to delete comment"), error))
  }

  addAttachment(processId, versionId, file) {
    const data = new FormData()
    data.append("attachment", file)

    return api.post(`/processes/${processId}/${versionId}/activity/attachments`, data)
      .then(() => this.addInfo(i18next.t("notification.error.attachmentAdded", "Attachment added")))
      .catch(error => this.addError(i18next.t("notification.error.failedToAddAttachment", "Failed to add attachment"), error))
  }

  downloadAttachment(processId, processVersionId, attachmentId) {
    window.open(`${API_URL}/processes/${processId}/${processVersionId}/activity/attachments/${attachmentId}`)
  }

  changeProcessName(processName, newProcessName): Promise<boolean> {
    const failedToChangeNameMessage = i18next.t("notification.error.failedToChangeName", "Failed to change scenario name:")
    if (newProcessName == null || newProcessName === "") {
      this.addErrorMessage(failedToChangeNameMessage, i18next.t("notification.error.newNameEmpty", "Name cannot be empty"), true)
      return Promise.resolve(false)
    }

    return api.put(`/processes/${processName}/rename/${newProcessName}`)
      .then(() => {
        this.addInfo(i18next.t("notification.error.nameChanged", "Process name changed"))
        return true
      })
      .catch((error) => {
        return this.addError(failedToChangeNameMessage, error, true).then(() => false)
      })
  }

  exportProcess(process, versionId) {
    return api.post("/processesExport", process, {responseType: "blob"})
      .then(response => FileSaver.saveAs(response.data, `${process.id}-${versionId}.json`))
      .catch(error => this.addError(i18next.t("notification.error.failedToExport", "Failed to export"), error))
  }

  exportProcessToPdf(processId, versionId, data, businessView) {
    return api.post(`/processesExport/pdf/${processId}/${versionId}`, data, {responseType: "blob", params: {businessView}})
      .then(response => FileSaver.saveAs(response.data, `${processId}-${versionId}.pdf`))
      .catch(error => this.addError(i18next.t("notification.error.failedToExportPdf", "Failed to export PDF"), error))
  }

  //This method will return *FAILED* promise if validation fails with e.g. 400 (fatal validation error)
  //to prevent closing edit node modal and corrupting graph display
  validateProcess(process) {
    return api.post("/processValidation", process)
      .catch(error => {
        this.addError(i18next.t("notification.error.fatalValidationError", "Fatal validation error, cannot save"), error, true)
        return Promise.reject(error)
      })
  }

  validateNode(processId, node) {
    const promise = api.post(`/nodes/${processId}/validation`, node)
    promise.catch(error => this.addError(i18next.t("notification.error.failedToValidateNode", "Failed to get node validation"), error, true))
    return promise
  }

  getNodeAdditionalData(processId, node) {
    const promise = api.post(`/nodes/${processId}/additionalData`, node)
    promise.catch(error => this.addError(i18next.t("notification.error.failedToFetchState", "Failed to get node additional data"), error, true))
    return promise
  }

  getTestCapabilities(process) {
    return api.post("/testInfo/capabilities", process)
      .catch(error => this.addError(i18next.t("notification.error.failedToGetCapabilities", "Failed to get capabilities"), error, true))
  }

  generateTestData(processId, testSampleSize, data) {
    return api.post(`/testInfo/generate/${testSampleSize}`, data, {responseType: "blob"})
      .then(response => FileSaver.saveAs(response.data, `${processId}-testData`))
      .catch(error => this.addError(i18next.t("notification.error.failedToGenerateTestData", "Failed to generate test data"), error))
  }

  fetchProcessCounts(processId: string, dateFrom: Moment, dateTo: Moment) {
    //we use offset date time instead of timestamp to pass info about user time zone to BE
    const format = (date: Moment) => date?.format('YYYY-MM-DDTHH:mm:ssZ')

    const data = {dateFrom: format(dateFrom), dateTo: format(dateTo)}
    const promise = api.get(`/processCounts/${processId}`, {params: data})

    promise.catch(error => this.addError(i18next.t("notification.error.failedToFetchCounts", "Cannot fetch process counts"), error, true))
    return promise
  }

  //This method will return *FAILED* promise if save/validation fails with e.g. 400 (fatal validation error)
  //to prevent closing edit node modal and corrupting graph display
  saveProcess(processId, processJson, comment) {
    const data = {process: processJson, comment: comment}
    return api.put(`/processes/${processId}`, data)
      .then(() => this.addInfo(i18next.t("notification.info.scenarioSaved", "Scenario {{processId}} was saved", processId)))
      .catch(error => {
        this.addError(i18next.t("notification.error.failedToSave", "Failed to save"), error, true)
        return Promise.reject(error)
      })
  }

  archiveProcess(processId) {
    return api.post(`/archive/${processId}`)
      .catch(error => this.addError(i18next.t("notification.error.failedToArchive", "Failed to archive scenario"), error, true))
  }

  unArchiveProcess(processId) {
    return api.post(`/unarchive/${processId}`)
      .catch(error => this.addError(i18next.t("notification.error.failedToUnArchive", "Failed to unarchive scenario"), error, true))
  }

  createProcess(processId, processCategory, isSubprocess) {
    return api.post(`/processes/${processId}/${processCategory}?isSubprocess=${isSubprocess}`)
      .catch(error => this.addError(i18next.t("notification.error.failedToCreate", "Failed to create scenario:"), error, true))
  }

  importProcess(processId, file) {
    const data = new FormData()
    data.append("process", file)

    return api.post(`/processes/import/${processId}`, data)
      .catch(error => this.addError(i18next.t("notification.error.failedToImport", "Failed to import"), error, true))
  }

  testProcess(processId, file, processJson) {
    const data = new FormData()
    data.append("testData", file)
    data.append("processJson", new Blob([JSON.stringify(processJson)], {type: "application/json"}))

    return api.post(`/processManagement/test/${processId}`, data)
      .catch(error => this.addError(i18next.t("notification.error.failedToTest", "Failed to test"), error, true))
  }

  compareProcesses(processId, thisVersion, otherVersion, businessView, remoteEnv) {
    const path = remoteEnv ? "remoteEnvironment" : "processes"

    return api.get(`/${path}/${processId}/${thisVersion}/compare/${otherVersion}`, {params: {businessView}})
      .catch(error => this.addError(i18next.t("notification.error.cannotCompare", "Cannot compare scenarios"), error, true))
  }

  fetchRemoteVersions(processId) {
    return api.get(`/remoteEnvironment/${processId}/versions`)
      .catch(error => this.addError(i18next.t("notification.error.failedToGetVersions", "Failed to get versions from second environment"), error))
  }

  migrateProcess(processId, versionId) {
    return api.post(`/remoteEnvironment/${processId}/${versionId}/migrate`)
      .then(() => this.addInfo(i18next.t("notification.info.scenarioMigrated", "Scenario {{processId}} was migrated")))
      .catch(error => this.addError(i18next.t("notification.error.failedToMigrate", "Failed to migrate"), error, true))
  }

  fetchSignals() {
    return api.get("/signal")
      .catch(error => this.addError(i18next.t("notification.error.failedToFetchSignals", "Failed to fetch signals"), error))
  }

  sendSignal(signalType, processId, params) {
    return api.post(`/signal/${signalType}/${processId}`, params)
      .then(() => this.addInfo(i18next.t("notification.info.signalSent", "Signal sent")))
      .catch(error => this.addError(i18next.t("notification.error.failedToSendSignal", "Failed to send signal"), error))
  }

  fetchOAuth2AccessToken<T>(authorizeCode: string | string[]) {
    return api.get<T>(`/authentication/oauth2?code=${authorizeCode}`)
  }

  fetchAuthenticationSettings<T>(authenticationProvider: string) {
    return api.get<T>(`/authentication/${authenticationProvider.toLowerCase()}/settings`)
  }

  async fetchProcessesNames() {
    const responses = await Promise.all([
      this.fetchProcesses(),
      this.fetchProcesses({isArchived: true}),
      this.fetchCustomProcesses(),
    ])
    return responses
      .reduce((result, {data}) => result.concat(data), [])
      .map(process => process.name)
  }
}

export default new HttpService()

