/* eslint-disable i18next/no-literal-string */
import {AxiosError, AxiosResponse} from "axios"
import FileSaver from "file-saver"
import i18next from "i18next"
import {Moment} from "moment"
import {SettingsData, ValidationData} from "../actions/nk"
import api from "../api"
import {UserData} from "../common/models/User"
import {ProcessActionType, ProcessStateType, ProcessType} from "../components/Process/types"
import {ToolbarsConfig} from "../components/toolbarSettings/types"
import {AuthenticationSettings} from "../reducers/settings"
import {Process, ProcessDefinitionData, ProcessId} from "../types"
import {WithId, Instant} from "../types/common"
import {BackendNotification} from "../containers/Notifications"
import {ProcessCounts} from "../reducers/graph"
import {TestResults} from "../common/TestResultUtils"
import {AdditionalInfo} from "../components/graph/node-modal/NodeAdditionalInfoBox"
import {withoutHackOfEmptyEdges} from "../components/graph/GraphPartialsInTS/EdgeUtils";

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

  fetchCategoriesWithProcessingType() {
    return api.get<Map<string, string>>("/app/config/categoriesWithProcessingType")
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

  /**
   * @deprecated
   */
  fetchComponentIds() {
    // `id` is not the same as in //api/component response
    return api.get<string[]>("/processDefinitionData/componentIds")
  }

  fetchDictLabelSuggestions(processingType, dictId, labelPattern) {
    return api.get(`/processDefinitionData/${processingType}/dict/${dictId}/entry?label=${labelPattern}`)
  }

  fetchComponents(): Promise<AxiosResponse<ComponentType[]>> {
    return api.get<ComponentType[]>("/components")
  }

  fetchComponentUsages(componentId: string): Promise<AxiosResponse<ComponentUsageType[]>> {
    return api.get<ComponentUsageType[]>(`/components/${encodeURIComponent(componentId)}/usages`)
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

  fetchProcessToolbarsConfiguration(processId) {
    const promise = api.get<WithId<ToolbarsConfig>>(`/processes/${encodeURIComponent(processId)}/toolbars`)
    promise.catch(error => this.#addError(i18next.t(
      "notification.error.cannotFetchToolbarConfiguration",
      "Cannot fetch toolbars configuration"
    ), error))
    return promise
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

  deploy(processId, comment?): Promise<{ isSuccess: boolean }> {
    return api.post(`/processManagement/deploy/${encodeURIComponent(processId)}`, comment).then(() => {
      return {isSuccess: true}
    }).catch(error => {
      if (error?.response?.status != 400) {
        return this.#addError(i18next.t("notification.error.failedToDeploy", "Failed to deploy {{processId}}", {processId}), error, true)
          .then((error) => {
            return {isSuccess: false}
          })
      } else {
        throw error
      }
    })
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

  exportProcess(process: Process, versionId) {
    return api.post("/processesExport", this.#sanitizeProcess(process), {responseType: "blob"})
      .then(response => FileSaver.saveAs(response.data, `${process.id}-${versionId}.json`))
      .catch(error => this.#addError(i18next.t("notification.error.failedToExport", "Failed to export"), error))
  }

  exportProcessToPdf(processId, versionId, data) {
    return api.post(`/processesExport/pdf/${encodeURIComponent(processId)}/${versionId}`, data, {responseType: "blob"})
      .then(response => FileSaver.saveAs(response.data, `${processId}-${versionId}.pdf`))
      .catch(error => this.#addError(i18next.t("notification.error.failedToExportPdf", "Failed to export PDF"), error))
  }

  //to prevent closing edit node modal and corrupting graph display
  validateProcess(process: Process) {
    return api.post("/processValidation", this.#sanitizeProcess(process))
      .catch(error => {
        this.#addError(i18next.t("notification.error.fatalValidationError", "Fatal validation error, cannot save"), error, true)
        return Promise.reject(error)
      })
  }

  validateNode(processId, node): Promise<AxiosResponse<ValidationData>> {
    const promise = api.post(`/nodes/${encodeURIComponent(processId)}/validation`, node)
    promise.catch(error => this.#addError(
      i18next.t("notification.error.failedToValidateNode", "Failed to get node validation"),
      error,
      true
    ))
    return promise
  }

  validateProperties(processId, processProperties): Promise<AxiosResponse<ValidationData>> {
    const promise = api.post(`/properties/${encodeURIComponent(processId)}/validation`, {processProperties})
    promise.catch(error => this.#addError(
        i18next.t("notification.error.failedToValidateProperties", "Failed to get properties validation"),
        error,
        true
    ))
    return promise
  }

  getNodeAdditionalInfo(processId, node): Promise<AxiosResponse<AdditionalInfo>> {
    const promise = api.post<AdditionalInfo>(`/nodes/${encodeURIComponent(processId)}/additionalInfo`, node)
    promise.catch(error => this.#addError(
      i18next.t("notification.error.failedToFetchNodeAdditionalInfo", "Failed to get node additional info"),
      error,
      true
    ))
    return promise
  }

  getPropertiesAdditionalInfo(processId, processProperties): Promise<AxiosResponse<AdditionalInfo>> {
    const promise = api.post<AdditionalInfo>(`/properties/${encodeURIComponent(processId)}/additionalInfo`, processProperties)
    promise.catch(error => this.#addError(
      i18next.t("notification.error.failedToFetchPropertiesAdditionalInfo", "Failed to get properties additional info"),
      error,
      true
    ))
    return promise
  }

  //This method will return *FAILED* promise if validation fails with e.g. 400 (fatal validation error)

  getTestCapabilities(process: Process) {
    return api.post("/testInfo/capabilities", this.#sanitizeProcess(process))
      .catch(error => this.#addError(i18next.t("notification.error.failedToGetCapabilities", "Failed to get capabilities"), error, true))
  }

  generateTestData(processId: string, testSampleSize: string, process: Process): Promise<AxiosResponse<any>> {
    const promise = api.post(`/testInfo/generate/${testSampleSize}`, this.#sanitizeProcess(process), {responseType: "blob"})
    promise
      .then(response => FileSaver.saveAs(response.data, `${processId}-testData`))
      .catch(error => this.#addError(i18next.t("notification.error.failedToGenerateTestData", "Failed to generate test data"), error, true))
    return promise
  }

  fetchProcessCounts(processId: string, dateFrom: Moment, dateTo: Moment): Promise<AxiosResponse<ProcessCounts>> {
    //we use offset date time instead of timestamp to pass info about user time zone to BE
    const format = (date: Moment) => date?.format("YYYY-MM-DDTHH:mm:ssZ")

    const data = {dateFrom: format(dateFrom), dateTo: format(dateTo)}
    const promise = api.get(`/processCounts/${encodeURIComponent(processId)}`, {params: data})

    promise.catch(error => this.#addError(i18next.t("notification.error.failedToFetchCounts", "Cannot fetch process counts"), error, true))
    return promise
  }

  //to prevent closing edit node modal and corrupting graph display
  saveProcess(processId, processJson: Process, comment) {
    const data = {process: this.#sanitizeProcess(processJson), comment: comment}
    return api.put(`/processes/${encodeURIComponent(processId)}`, data)
      .catch(error => {
        this.#addError(i18next.t("notification.error.failedToSave", "Failed to save"), error, true)
        return Promise.reject(error)
      })
  }

  archiveProcess(processId) {
    return api.post(`/archive/${encodeURIComponent(processId)}`)
      .catch(error => this.#addError(i18next.t("notification.error.failedToArchive", "Failed to archive scenario"), error, true))
  }

  unArchiveProcess(processId) {
    return api.post(`/unarchive/${encodeURIComponent(processId)}`)
      .catch(error => this.#addError(i18next.t("notification.error.failedToUnArchive", "Failed to unarchive scenario"), error, true))
  }

  //This method will return *FAILED* promise if save/validation fails with e.g. 400 (fatal validation error)

  createProcess(processId: string, processCategory: string, isSubprocess = false) {
    const promise = api.post(`/processes/${encodeURIComponent(processId)}/${processCategory}?isSubprocess=${isSubprocess}`)
    promise.catch(error => {
      if(error?.response?.status != 400)
        this.#addError(i18next.t("notification.error.failedToCreate", "Failed to create scenario:"), error, true)
    })
    return promise
  }

  importProcess(processId, file) {
    const data = new FormData()
    data.append("process", file)

    return api.post(`/processes/import/${encodeURIComponent(processId)}`, data)
      .catch(error => this.#addError(i18next.t("notification.error.failedToImport", "Failed to import"), error, true))
  }

  testProcess(processId: ProcessId, file: File, processJson: Process): Promise<AxiosResponse<TestProcessResponse>> {
    const sanitized = this.#sanitizeProcess(processJson)

    const data = new FormData()
    data.append("testData", file)
    data.append("processJson", new Blob([JSON.stringify(sanitized)], {type: "application/json"}))

    const promise = api.post(`/processManagement/test/${encodeURIComponent(processId)}`, data)
    promise.catch(error => this.#addError(i18next.t("notification.error.failedToTest", "Failed to test"), error, true))
    return promise
  }

  compareProcesses(processId, thisVersion, otherVersion, remoteEnv) {
    const path = remoteEnv ? "remoteEnvironment" : "processes"

    const promise = api.get(`/${path}/${encodeURIComponent(processId)}/${thisVersion}/compare/${otherVersion}`)
    promise.catch(error => this.#addError(i18next.t("notification.error.cannotCompare", "Cannot compare scenarios"), error, true))
    return promise
  }

  fetchRemoteVersions(processId) {
    const promise = api.get(`/remoteEnvironment/${encodeURIComponent(processId)}/versions`)
    promise.catch(error => this.#addError(i18next.t(
      "notification.error.failedToGetVersions",
      "Failed to get versions from second environment"
    ), error))
    return promise
  }

  migrateProcess(processId, versionId) {
    return api.post(`/remoteEnvironment/${encodeURIComponent(processId)}/${versionId}/migrate`)
      .then(() => this.#addInfo(i18next.t("notification.info.scenarioMigrated", "Scenario {{processId}} was migrated", {processId})))
      .catch(error => this.#addError(i18next.t("notification.error.failedToMigrate", "Failed to migrate"), error, true))
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

  #sanitizeProcess(process: Process) {
    //don't send validationResult, it's not needed and can be v. large,
    const {id, nodes, edges, properties, processingType, category}: Omit<Process, "validationResult">
      //don't send empty edges
      = withoutHackOfEmptyEdges(process)
    return {id, nodes, edges, properties, processingType, category}
  }

}

export default new HttpService()

