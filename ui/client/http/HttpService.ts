/* eslint-disable i18next/no-literal-string */
import {AxiosError} from "axios"
import FileSaver from "file-saver"
import api from "../api"
import {ProcessStateType, ProcessType} from "../components/Process/types"
import {API_URL} from "../config"

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
      .catch(error => this.addError("Cannot fetch state", error))
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
    return api.get("/settings")
  }

  fetchLoggedUser() {
    return api.get("/user")
  }

  fetchProcessDefinitionData(processingType, isSubprocess, data) {
    const promise = api.post(`/processDefinitionData/${processingType}?isSubprocess=${isSubprocess}`, data)
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
    promise.catch((error) => this.addError("Cannot find chosen versions", error, true))
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
      .catch(error => Promise.reject(this.addError("Cannot fetch statuses", error)))
  }

  fetchProcessState(processId) {
    return api.get(`/processes/${processId}/status`)
      .catch(error => this.addError("Cannot fetch status", error))
  }

  deploy(processId, comment?) {
    return api.post(`/processManagement/deploy/${processId}`, comment).then(() => {
      this.addInfo(`Process ${processId} was deployed`)
      return {isSuccess: true}
    }).catch(error => {
      return this.addError(`Failed to deploy ${processId}`, error, true).then(() => {
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
      .then(() => this.addInfo(`Process ${processId} was canceled`))
      .catch(error => this.addError(`Failed to cancel ${processId}`, error, true))
  }

  fetchProcessActivity(processId) {
    return api.get(`/processes/${processId}/activity`)
  }

  addComment(processId, versionId, data) {
    return api.post(`/processes/${processId}/${versionId}/activity/comments`, data)
      .then(() => this.addInfo("Comment added"))
      .catch(error => this.addError("Failed to add comment", error))
  }

  deleteComment(processId, commentId) {
    return api.delete(`/processes/${processId}/activity/comments/${commentId}`)
      .then(() => this.addInfo("Comment deleted"))
      .catch(error => this.addError("Failed to delete comment", error))
  }

  addAttachment(processId, versionId, file) {
    const data = new FormData()
    data.append("attachment", file)

    return api.post(`/processes/${processId}/${versionId}/activity/attachments`, data)
      .then(() => this.addInfo("Attachment added"))
      .catch(error => this.addError("Failed to add attachment", error))
  }

  downloadAttachment(processId, processVersionId, attachmentId) {
    window.open(`${API_URL}/processes/${processId}/${processVersionId}/activity/attachments/${attachmentId}`)
  }

  changeProcessName(processName, newProcessName): Promise<boolean> {
    if (newProcessName == null || newProcessName === "") {
      this.addErrorMessage("Failed to change process name:", "Name cannot be empty", true)
      return Promise.resolve(false)
    }

    return api.put(`/processes/${processName}/rename/${newProcessName}`)
      .then(() => {
        this.addInfo("Process name changed")
        return true
      })
      .catch((error) => {
        return this.addError("Failed to change process name:", error, true).then(() => false)
      })
  }

  exportProcess(process, versionId) {
    return api.post("/processesExport", process, {responseType: "blob"})
      .then(response => FileSaver.saveAs(response.data, `${process.id}-${versionId}.json`))
      .catch(error => this.addError("Failed to export", error))
  }

  exportProcessToPdf(processId, versionId, data, businessView) {
    return api.post(`/processesExport/pdf/${processId}/${versionId}`, data, {responseType: "blob", params: {businessView}})
      .then(response => FileSaver.saveAs(response.data, `${processId}-${versionId}.pdf`))
      .catch(error => this.addError("Failed to export", error))
  }

  //This method will return *FAILED* promise if validation fails with e.g. 400 (fatal validation error)
  //to prevent closing edit node modal and corrupting graph display
  validateProcess(process) {
    return api.post("/processValidation", process)
      .catch(error => {
        this.addError("Fatal validation error, cannot save", error, true)
        return Promise.reject(error)
      })
  }

  validateNode(processId, node) {
    const promise = api.post(`/nodes/${processId}/validation`, node)
    promise.catch(error => this.addError("Failed to get node validation", error, true))
    return promise
  }

  getNodeAdditionalData(processId, node) {
    const promise = api.post(`/nodes/${processId}/additionalData`, node)
    promise.catch(error => this.addError("Failed to get node additional data", error, true))
    return promise
  }

  getTestCapabilities(process) {
    return api.post("/testInfo/capabilities", process)
      .catch(error => this.addError("Failed to get capabilities", error, true))
  }

  generateTestData(processId, testSampleSize, data) {
    return api.post(`/testInfo/generate/${testSampleSize}`, data, {responseType: "blob"})
      .then(response => FileSaver.saveAs(response.data, `${processId}-testData`))
      .catch(error => this.addError("Failed to generate test data", error))
  }

  fetchProcessCounts(processId, dateFrom, dateTo) {
    const data = {dateFrom: dateFrom, dateTo: dateTo}
    const promise = api.get(`/processCounts/${processId}`, {params: data})

    promise.catch(error => this.addError("Cannot fetch process counts", error, true))
    return promise
  }

  //This method will return *FAILED* promise if save/validation fails with e.g. 400 (fatal validation error)
  //to prevent closing edit node modal and corrupting graph display
  saveProcess(processId, processJson, comment) {
    const data = {process: processJson, comment: comment}
    return api.put(`/processes/${processId}`, data)
      .then(() => this.addInfo(`Process ${processId} was saved`))
      .catch(error => {
        this.addError("Failed to save", error, true)
        return Promise.reject(error)
      })
  }

  archiveProcess(processId) {
    return api.post(`/archive/${processId}`)
      .catch(error => this.addError("Failed to archive process", error, true))
  }

  unArchiveProcess(processId) {
    return api.post(`/unarchive/${processId}`)
      .catch(error => this.addError("Failed to unarchive process", error, true))
  }

  createProcess(processId, processCategory, isSubprocess) {
    return api.post(`/processes/${processId}/${processCategory}?isSubprocess=${isSubprocess}`)
      .catch(error => this.addError("Failed to create process:", error, true))
  }

  importProcess(processId, file) {
    const data = new FormData()
    data.append("process", file)

    return api.post(`/processes/import/${processId}`, data)
      .catch(error => this.addError("Failed to import", error, true))
  }

  testProcess(processId, file, processJson) {
    const data = new FormData()
    data.append("testData", file)
    data.append("processJson", new Blob([JSON.stringify(processJson)], {type: "application/json"}))

    return api.post(`/processManagement/test/${processId}`, data)
      .catch(error => this.addError("Failed to test", error, true))
  }

  compareProcesses(processId, thisVersion, otherVersion, businessView, remoteEnv) {
    const path = remoteEnv ? "remoteEnvironment" : "processes"

    return api.get(`/${path}/${processId}/${thisVersion}/compare/${otherVersion}`, {params: {businessView}})
      .catch(error => this.addError("Cannot compare processes", error, true))
  }

  fetchRemoteVersions(processId) {
    return api.get(`/remoteEnvironment/${processId}/versions`)
      .catch(error => this.addError("Failed to get versions from second environment", error))
  }

  migrateProcess(processId, versionId) {
    return api.post(`/remoteEnvironment/${processId}/${versionId}/migrate`)
      .then(() => this.addInfo(`Process ${processId} was migrated`))
      .catch(error => this.addError("Failed to migrate", error, true))
  }

  fetchSignals() {
    return api.get("/signal")
      .catch(error => this.addError("Failed to fetch signals", error))
  }

  sendSignal(signalType, processId, params) {
    return api.post(`/signal/${signalType}/${processId}`, params)
      .then(() => this.addInfo("Signal send"))
      .catch(error => this.addError("Failed to send signal", error))
  }

  fetchOAuth2AccessToken(authorizeCode) {
    return api.get(`/authentication/oauth2?code=${authorizeCode}`)
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
