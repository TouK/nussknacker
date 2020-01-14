import {API_URL} from "../config"
import React from "react"
import FileSaver from "file-saver"
import api from "../api"
import * as _ from "lodash"
import * as queryString from "query-string";

let notificationActions = null
let notificationReload = null

//TODO: Move show information about error to another place. HttpService should avoid only action (get / post / etc..) - handling errors should be in another place.
export default {
  setNotificationActions(na) {
    notificationActions = na
    if (notificationReload) {
      clearInterval(notificationReload)
    }
    //TODO: configuration?
    notificationReload = setInterval(() => this._loadNotifications(), 10000)
  },

  _loadNotifications() {
    api.get("/notifications").then(response => response.data.forEach(notification => {
      notification.type === "info" ? this.addInfo(notification.message) : this.addError(notification.message)
    }))
  },

  addInfo(message) {
    if (notificationActions) {
      notificationActions.success(message)
    }
  },

  addErrorMessage(message, error, showErrorText) {
    if (notificationActions) {
      notificationActions.error(message, error, showErrorText)
    }
  },

  addError(message, error, showErrorText) {
    console.warn(error)
    const errorMessage = _.get(error, "response.data") || error.message
    this.addErrorMessage(message, errorMessage, showErrorText)
    return Promise.resolve(error)
  },

  availableQueryableStates() {
    return api.get("/queryableState/list")
  },

  queryState(processId, queryName, key) {
    const data = {processId, queryName, key}
    return api.get("/queryableState/fetch", {params: data})
      .catch(error => this.addError("Cannot fetch state", error))
  },

  fetchHealthCheck() {
    return api.get("/app/healthCheck")
      .then(() => ({state: "ok"}))
      .catch((error) => ({state: "error", error: error.response.data}))
  },

  fetchSettings() {
    return api.get("/settings")
  },

  fetchLoggedUser() {
    return api.get("/user")
  },

  fetchProcessDefinitionData(processingType, isSubprocess, data) {
    return api.post(`/processDefinitionData/${processingType}?isSubprocess=${isSubprocess}`, data)
      .then((response => {
        // This is a walk-around for having part of node template (branch parameters) outside of itself.
        // See note in DefinitionPreparer on backend side. // TODO remove it after API refactor
        response.data.nodesToAdd.forEach(nodeAggregates => {
          nodeAggregates.possibleNodes.forEach(nodeToAdd => {
            nodeToAdd.node.branchParametersTemplate = nodeToAdd.branchParametersTemplate
          })
        })

        return response
      }))
      .catch((error) => this.addError("Cannot find chosen versions", error, true))
  },

  fetchComponentIds() {
    return api.get("/processDefinitionData/componentIds")
  },

  fetchServices() {
    return api.get("/processDefinitionData/services")
  },

  fetchDictLabelSuggestions(processingType, dictId, labelPattern) {
    return api.get(`/processDefinitionData/${processingType}/dict/${dictId}/entry?label=${labelPattern}`)
  },

  fetchUnusedComponents() {
    return api.get("/app/unusedComponents")
  },

  fetchProcessesComponents(componentId) {
    return api.get(`/processesComponents/${  encodeURIComponent(componentId)}`)
  },

  fetchProcesses(data) {
    return api.get("/processes", {params: data})
  },

  fetchCustomProcesses() {
    return api.get("/customProcesses")
  },

  fetchProcessDetails(processId, versionId, businessView) {
    let url = versionId ? `/processes/${processId}/${versionId}` : `/processes/${processId}`
    const queryParams = this.businessViewQueryParams(businessView)
    return api.get(url, {params: queryParams})
  },

  fetchProcessesStatus() {
    return api.get("/processes/status")
      .catch(error => this.addError("Cannot fetch statuses", error))
  },

  fetchSingleProcessStatus(processId) {
    return api.get(`/processes/${processId}/status`)
      .catch(error => this.addError("Cannot fetch status", error))
  },

  deploy(processId, comment) {
    return api.post(`/processManagement/deploy/${processId}`, comment).then(() => {
      this.addInfo(`Process ${processId} was deployed`)
      return {isSuccess: true}
    }).catch((error) => {
      this.addError(`Failed to deploy ${processId}`, error, true)
      return {isSuccess: false}
    })
  },

  invokeService(processingType, serviceName, parameters) {
    return api.post(`/service/${processingType}/${serviceName}`, parameters)
  },

  cancel(processId, comment) {
    return api.post(`/processManagement/cancel/${processId}`, comment)
      .then(() => this.addInfo(`Process ${processId} was canceled`))
      .catch(error => this.addError(`Failed to cancel ${processId}`, error, true))
  },

  fetchProcessActivity(processId) {
    return api.get(`/processes/${processId}/activity`)
  },

  addComment(processId, versionId, data) {
    return api.post(`/processes/${processId}/${versionId}/activity/comments`, data)
      .then(() => this.addInfo("Comment added"))
      .catch(error => this.addError("Failed to add comment", error))
  },

  deleteComment(processId, commentId) {
    return api.delete(`/processes/${processId}/activity/comments/${commentId}`)
      .then(() => this.addInfo("Comment deleted"))
      .catch(error => this.addError("Failed to delete comment", error))
  },

  addAttachment(processId, versionId, file) {
    let data = new FormData()
    data.append("attachment", file)

    return api.post(`/processes/${processId}/${versionId}/activity/attachments`, data)
      .then(() => this.addInfo("Attachment added"))
      .catch(error => this.addError("Failed to add attachment", error))
  },

  downloadAttachment(processId, processVersionId, attachmentId) {
    window.open(`${API_URL}/processes/${processId}/${processVersionId}/activity/attachments/${attachmentId}`)
  },

  changeProcessName(processName, newProcessName) {
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
        this.addError("Failed to change process name:", error, true)
        return false
      })
  },

  exportProcess(process, versionId) {
    return api.post("/processesExport", process, {responseType: "blob"})
      .then(response => FileSaver.saveAs(response.data, `${process.id}-${versionId}.json`))
      .catch(error => this.addError("Failed to export", error))
  },

  exportProcessToPdf(processId, versionId, data, businessView) {
    const url = `/processesExport/pdf/${processId}/${versionId}`
    const queryParams = this.businessViewQueryParams(businessView)

    return api.post(queryParams ? `${url}?${queryParams}` : url, data, {responseType: "blob"})
      .then(response => FileSaver.saveAs(response.data, `${processId}-${versionId}.pdf`))
      .catch(error => this.addError("Failed to export", error))
  },

  validateProcess(process) {
    return api.post("/processValidation", process)
      .catch(error => this.addError("Fatal validation error, cannot save", error, true))
  },

  getTestCapabilities(process) {
    return api.post("/testInfo/capabilities", process)
      .catch(error => this.addError("Failed to get capabilities", error, true))
  },

  generateTestData(processId, testSampleSize, data) {
    return api.post(`/testInfo/generate/${testSampleSize}`, data, {responseType: "blob"})
      .then(response => FileSaver.saveAs(response.data, `${processId}-testData`))
      .catch(error => this.addError("Failed to generate test data", error))
  },

  fetchProcessCounts(processId, dateFrom, dateTo) {
    const data = {dateFrom: dateFrom, dateTo: dateTo}

    return api.get(`/processCounts/${processId}`, {params: data})
      .catch(error => this.addError("Cannot fetch process counts", error, true))
  },

  saveProcess(processId, processJson, comment) {
    const data = {process: processJson, comment: comment}
    return api.put(`/processes/${processId}`, data)
      .then(() => this.addInfo(`Process ${processId} was saved`))
      .catch(error => this.addError("Failed to save", error, true))
  },

  archiveProcess(processId) {
    return api.post(`/archive/${processId}`, {isArchived: true})
      .catch(error => this.addError("Failed to archive process", error, true))
  },

  createProcess(processId, processCategory, isSubprocess) {
    return api.post(`/processes/${processId}/${processCategory}?isSubprocess=${isSubprocess}`)
      .catch(error => this.addError("Failed to create process:", error, true))
  },

  importProcess(processId, file) {
    const data = new FormData()
    data.append("process", file)

    return api.post(`/processes/import/${processId}`, data)
      .catch(error => this.addError("Failed to import", error, true))
  },

  testProcess(processId, file, processJson, callback, errorCallback) {
    let data = new FormData()
    data.append("testData", file)
    data.append("processJson", new Blob([JSON.stringify(processJson)], {type: "application/json"}))

    return api.post(`/processManagement/test/${processId}`, data)
      .catch(error => this.addError("Failed to test", error, true))
  },

  compareProcesses(processId, thisVersion, otherVersion, businessView, remoteEnv) {
    const queryParams = this.businessViewQueryParams(businessView)
    const path = remoteEnv ? "remoteEnvironment" : "processes"

    return api.get(`/${path}/${processId}/${thisVersion}/compare/${otherVersion}`, {params: queryParams})
      .catch(error => this.addError("Cannot compare processes", error, true))
  },

  fetchRemoteVersions(processId) {
    return api.get(`/remoteEnvironment/${processId}/versions`)
      .catch(error => this.addError("Failed to get versions from second environment", error))
  },

  migrateProcess(processId, versionId) {
    return api.post(`/remoteEnvironment/${processId}/${versionId}/migrate`)
      .then(() => this.addInfo(`Process ${processId} was migrated`))
      .catch(error => this.addError("Failed to migrate", error, true))
  },

  fetchSignals() {
    return api.get("/signal")
      .catch(error => this.addError("Failed to fetch signals", error))
  },

  sendSignal(signalType, processId, params) {
    return api.post(`/signal/${signalType}/${processId}`, params)
      .then(() => this.addInfo("Signal send"))
      .catch(error => this.addError("Failed to send signal", error))
  },

  businessViewQueryParams(businessView) {
    return businessView ? queryString.stringify({businessView}) : null
  },

  fetchOAuth2AccessToken(authorizeCode) {
    return api.get(`/authentication/oauth2?code=${authorizeCode}`)
  }
}
