/* eslint-disable i18next/no-literal-string */
import type { AxiosError, AxiosResponse } from "axios";
import axios from "axios";
import FileSaver from "file-saver";
import i18next from "i18next";
import type { Moment } from "moment";

import type { GenericValidationData, GenericValidationRequest, TestAdhocValidationRequest } from "../../actions/nk/adhocTesting";
import type { SettingsData } from "../../actions/nk/assignSettings";
import type { ValidationData, ValidationRequest } from "../../actions/nk/nodeDetails";
import type { ProcessingType } from "../../actions/nk/processDefinitionData";
import api from "../../api";
import type { UserData } from "../../common/models/User";
import SystemUtils from "../../common/SystemUtils";
import type { ChatRequest } from "../../components/aiAssistant/ChatRequest";
import { withoutHackOfEmptyEdges } from "../../components/graph/GraphPartialsInTS/EdgeUtils";
import type { AdditionalInfo } from "../../components/graph/node-modal/AdditionalInfoBox";
import type { ExpressionSuggestion } from "../../components/graph/node-modal/editors/expression/ExpressionSuggester";
import type { ExpressionObj } from "../../components/graph/node-modal/editors/expression/types";
import { extractStickyNotesFromNodes } from "../../components/graph/utils/stickyNotesUtils";
import type { AvailableScenarioLabels, ScenarioLabelsValidationResponse } from "../../components/Labels/types";
import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import { mapInputDataRecordsToRunTestsFormat } from "../../components/modals/TestingDataRecords/utils";
import type { ProcessName, ProcessVersionId, Scenario, StatusDefinitionType } from "../../components/Process/types";
import type { ActivitiesResponse, ActivityMetadataResponse, ActivityType } from "../../components/toolbars/activities/types";
import { ActivityTypesRelatedToExecutions } from "../../components/toolbars/activities/types";
import type {
    ScenarioActionResult,
    ScenarioActionResultDeploySuccess,
    ScenarioActionResultSuccess,
    ScenarioActionUnhandledError,
} from "../../components/toolbars/scenarioActions/buttons/types";
import { ScenarioActionResultType } from "../../components/toolbars/scenarioActions/buttons/types";
import type { ToolbarsConfig } from "../../components/toolbarSettings/types";
import type { ProcessVersionValidationResponse } from "../../components/versionControl/types";
import { API_URL } from "../../config";
import type { EventTrackingSelectorType, EventTrackingType } from "../../containers/event-tracking/use-register-tracking-events";
import type { BackendNotification } from "../../containers/Notifications";
import { handleAxiosError } from "../../devHelpers";
import type { TestCase } from "../../reducers/graph/testCase";
import type { AuthenticationSettings } from "../../reducers/settings";
import type { WithId } from "../../types/common";
import type { Expression, NodeType, PropertiesType } from "../../types/node";
import type { ProcessDefinitionData, ScenarioGraph } from "../../types/scenarioGraph";
import type { ValidationResult, VariableTypes } from "../../types/validation";
import { fixAggregateParameters, fixBranchParametersTemplate } from "../parametersUtils";
import type { ProcessCounts, ResultsWithCountsDto } from "../resultsWithCountsDto";
import type {
    AppBuildInfo,
    ComponentType,
    ComponentUsageType,
    DeployResponse,
    DictOption,
    ExpressionSuggestionRequest,
    FetchProcessQueryParams,
    HealthCheckProcessDeploymentType,
    HealthCheckResponse,
    NodesDeploymentData,
    NotificationActions,
    ProcessDefinitionDataDictOption,
    PropertiesValidationRequest,
    ResponseStatus,
    ScenarioGraphSource,
    ScenarioParametersCombinations,
    SourceWithParametersTest,
    StatusesType,
    TestCaseNodeAdditionalVariablesRequest,
    TestCaseNodeAdditionalVariablesResponse,
} from "./types";
import { HealthState } from "./types";

export class HttpService {
    //TODO: Move show information about error to another place. HttpService should avoid only action (get / post / etc..) - handling errors should be in another place.
    #notificationActions: NotificationActions = null;
    #skipResultsPerTransition = null;

    setNotificationActions(na: NotificationActions) {
        this.#notificationActions = na;
    }

    loadBackendNotifications(scenarioName: string | undefined): Promise<BackendNotification[]> {
        const path = scenarioName !== undefined ? `/notifications?scenarioName=${scenarioName}` : `/notifications`;
        return api.get<BackendNotification[]>(path).then((d) => {
            return d.data;
        });
    }

    fetchHealthCheckProcessDeployment(): Promise<HealthCheckResponse> {
        return api
            .get("/app/healthCheck/process/deployment")
            .then(() => ({ state: HealthState.ok }))
            .catch((error) => {
                const { message, processes }: HealthCheckProcessDeploymentType = error.response?.data || {};
                return {
                    state: HealthState.error,
                    error: message,
                    processes: processes,
                };
            });
    }

    fetchSettings() {
        return api.get<SettingsData>("/settings");
    }

    fetchSettingsWithAuth(): Promise<
        SettingsData & {
            authentication: AuthenticationSettings;
        }
    > {
        return this.fetchSettings().then(({ data }) => {
            const { provider } = data.authentication;
            const settings = data;
            return this.fetchAuthenticationSettings(provider).then(({ data }) => {
                return {
                    ...settings,
                    authentication: {
                        ...settings.authentication,
                        ...data,
                    },
                };
            });
        });
    }

    fetchLoggedUser() {
        return api.get<UserData>("/user");
    }

    fetchAppBuildInfo() {
        return api.get<AppBuildInfo>("/app/buildInfo");
    }

    // This function is used only by external project
    fetchCategoriesWithProcessingType() {
        return api.get<Map<string, string>>("/app/config/categoriesWithProcessingType");
    }

    fetchProcessDefinitionData(processingType: string, isFragment: boolean) {
        const promise = api.get<ProcessDefinitionData>(`/processDefinitionData/${processingType}?isFragment=${isFragment}`).then(
            ({ data, ...response }): AxiosResponse<ProcessDefinitionData> => ({
                ...response,
                data: {
                    ...data,
                    componentGroups: data.componentGroups.map(({ components, ...group }) => ({
                        ...group,
                        components: components.map(fixBranchParametersTemplate).map(fixAggregateParameters),
                    })),
                },
            }),
        );
        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.cannotFindChosenVersions", "Cannot find chosen versions"), error, true),
        );
        return promise;
    }

    fetchDictLabelSuggestions(processingType, dictId, labelPattern) {
        return api.get(`/processDefinitionData/${processingType}/dicts/${dictId}/entry?label=${labelPattern}`);
    }

    fetchComponents(skipUsages: boolean, skipFragments: boolean): Promise<AxiosResponse<ComponentType[]>> {
        return api.get<ComponentType[]>(`/components?skipUsages=${skipUsages}&skipFragments=${skipFragments}`);
    }

    fetchComponentUsages(componentId: string): Promise<AxiosResponse<ComponentUsageType[]>> {
        return api.get<ComponentUsageType[]>(`/components/${encodeURIComponent(componentId)}/usages`);
    }

    fetchProcesses(data: FetchProcessQueryParams = {}): Promise<AxiosResponse<Scenario[]>> {
        return api.get<Scenario[]>("/processes", { params: data });
    }

    fetchScenario(
        processName: ProcessName,
        options: {
            versionId?: ProcessVersionId;
            skipValidation?: boolean;
        } = {},
    ): Promise<AxiosResponse<Scenario>> {
        let url = `/processes/${encodeURIComponent(processName)}`;
        if (options.versionId) url += `/${options.versionId}`;
        return api.get<Scenario>(url, {
            params: { skipValidateAndResolve: options.skipValidation },
        });
    }

    /**
     * @deprecated use HttpService.fetchScenario
     */
    fetchProcessDetails(processName: ProcessName, versionId?: ProcessVersionId): Promise<AxiosResponse<Scenario>> {
        return this.fetchScenario(processName, { versionId });
    }

    /**
     * @deprecated use HttpService.fetchScenario
     */
    fetchLatestProcessDetailsWithoutValidation(processName: ProcessName, versionId?: ProcessVersionId): Promise<AxiosResponse<Scenario>> {
        return this.fetchScenario(processName, { versionId, skipValidation: true });
    }

    fetchProcessesStates() {
        return api
            .get<StatusesType>("/processes/status")
            .catch((error) =>
                Promise.reject(this.#addError(i18next.t("notification.error.cannotFetchStatuses", "Cannot fetch statuses"), error)),
            );
    }

    fetchStatusDefinitions() {
        return api
            .get<StatusDefinitionType[]>(`/statusDefinitions`)
            .catch((error) =>
                Promise.reject(
                    this.#addError(i18next.t("notification.error.cannotFetchStatusDefinitions", "Cannot fetch status definitions"), error),
                ),
            );
    }

    fetchScenarioLabels() {
        return api
            .get<AvailableScenarioLabels>(`/scenarioLabels`)
            .catch((error) =>
                Promise.reject(
                    this.#addError(i18next.t("notification.error.cannotFetchScenarioLabels", "Cannot fetch scenario labels"), error),
                ),
            );
    }

    fetchProcessToolbarsConfiguration(processName) {
        const promise = api.get<WithId<ToolbarsConfig>>(`/processes/${encodeURIComponent(processName)}/toolbars`);
        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.cannotFetchToolbarConfiguration", "Cannot fetch toolbars configuration"), error),
        );
        return promise;
    }

    fetchProcessState(processName: ProcessName, processVersionId: number) {
        const promise = api.get(`/processes/${encodeURIComponent(processName)}/status`, {
            params: {
                currentlyPresentedVersionId: processVersionId,
            },
        });
        promise.catch((error) => this.#addError(i18next.t("notification.error.cannotFetchStatus", "Cannot fetch status"), error));
        return promise;
    }

    fetchActivitiesRelatedToExecutions(processName: string) {
        return api
            .get<{ activities: { date: string; type: ActivityType }[] }>(
                `/processes/${encodeURIComponent(processName)}/activity/activities`,
            )
            .then((res) => {
                return res.data.activities.filter(({ date, type }) =>
                    Object.values(ActivityTypesRelatedToExecutions).includes(type as ActivityTypesRelatedToExecutions),
                );
            })
            .then((res) => res.reverse().map((item) => ({ ...item, type: item.type as ActivityTypesRelatedToExecutions })));
    }

    async deploy(
        processName: string,
        comment?: string,
        nodesDeploymentData?: NodesDeploymentData,
        scenarioGraphSource?: ScenarioGraphSource,
    ): Promise<ScenarioActionResult> {
        const runDeploymentRequest = {
            nodesDeploymentData,
            comment,
            scenarioGraphSource: {
                ...scenarioGraphSource,
                scenarioGraph: scenarioGraphSource.scenarioGraph ? this.#sanitizeScenarioGraph(scenarioGraphSource.scenarioGraph) : null,
            },
        };
        return await api
            .post<DeployResponse>(`/processManagement/deploy/${encodeURIComponent(processName)}`, runDeploymentRequest)
            .then((resp) => {
                const result: ScenarioActionResultDeploySuccess = {
                    deployedScenarioVersionId: resp.data.deployedScenarioVersionId,
                    scenarioActionResultType: ScenarioActionResultType.DeploySuccess,
                };
                return result;
            })
            .catch((error: AxiosError) => {
                if (error?.response?.status != 400) {
                    return this.#addError(
                        i18next.t("notification.error.failedToDeploy", "Failed to deploy {{processName}} due to: {{axiosError}}", {
                            processName,
                            axiosError: handleAxiosError(error),
                        }),
                        error,
                        true,
                    ).then(() => {
                        return {
                            scenarioActionResultType: ScenarioActionResultType.UnhandledError,
                            msg: "Unknown error",
                        };
                    });
                } else {
                    const msg = error.response.data;
                    return {
                        scenarioActionResultType: ScenarioActionResultType.ValidationError,
                        msg: msg.toString(),
                    };
                }
            });
    }

    async redeploy(
        processName: string,
        comment?: string,
        nodesDeploymentData?: NodesDeploymentData,
        scenarioGraphSource?: ScenarioGraphSource,
    ): Promise<ScenarioActionResult> {
        const runDeploymentRequest = {
            nodesDeploymentData,
            comment,
            scenarioGraphSource: {
                ...scenarioGraphSource,
                scenarioGraph: scenarioGraphSource.scenarioGraph ? this.#sanitizeScenarioGraph(scenarioGraphSource.scenarioGraph) : null,
            },
        };
        return await api
            .post<DeployResponse>(`/processManagement/redeploy/${encodeURIComponent(processName)}`, runDeploymentRequest)
            .then((resp) => {
                const result: ScenarioActionResultDeploySuccess = {
                    deployedScenarioVersionId: resp.data.deployedScenarioVersionId,
                    scenarioActionResultType: ScenarioActionResultType.DeploySuccess,
                };
                return result;
            })
            .catch((error: AxiosError) => {
                if (error?.response?.status != 400) {
                    return this.#addError(
                        i18next.t("notification.error.failedToRedeploy", "Failed to redeploy {{processName}} due to: {{axiosError}}", {
                            processName,
                            axiosError: handleAxiosError(error),
                        }),
                        error,
                        true,
                    ).then(() => {
                        return {
                            scenarioActionResultType: ScenarioActionResultType.UnhandledError,
                            msg: "Unknown error",
                        };
                    });
                } else {
                    const msg = error.response.data;
                    return {
                        scenarioActionResultType: ScenarioActionResultType.ValidationError,
                        msg: msg.toString(),
                    };
                }
            });
    }

    runOffSchedule(processName: string, comment?: string): Promise<ScenarioActionResult> {
        const data = {
            comment: comment,
        };
        return api
            .post(`/processManagement/runOffSchedule/${encodeURIComponent(processName)}`, data)
            .then((res) => {
                const msg = res.data.msg;
                this.#addInfo(msg);
                const result: ScenarioActionResultSuccess = {
                    scenarioActionResultType: ScenarioActionResultType.Success,
                    msg: msg.toString(),
                };
                return result;
            })
            .catch((error) => {
                const msg = error.response.data.msg || error.response.data;
                const result: ScenarioActionUnhandledError = {
                    scenarioActionResultType: ScenarioActionResultType.UnhandledError,
                    msg: msg.toString(),
                };
                if (error?.response?.status != 400) return this.#addError(msg, error, false).then(() => result);
                return {
                    scenarioActionResultType: ScenarioActionResultType.ValidationError,
                    msg: msg.toString(),
                };
            });
    }

    cancel(processName, comment?): Promise<ScenarioActionResult> {
        return api
            .post(`/processManagement/cancel/${encodeURIComponent(processName)}`, comment)
            .then(() => {
                const result: ScenarioActionResultSuccess = {
                    scenarioActionResultType: ScenarioActionResultType.Success,
                    msg: "",
                };
                return result;
            })
            .catch((error) => {
                if (error?.response?.status != 400) {
                    return this.#addError(
                        i18next.t("notification.error.failedToCancel", "Failed to cancel {{processName}}", { processName }),
                        error,
                        true,
                    ).then(() => {
                        return {
                            scenarioActionResultType: ScenarioActionResultType.UnhandledError,
                            msg: "Unknown error occured",
                        };
                    });
                } else {
                    const msg = error.response.data.msg || error.response.data;
                    return {
                        scenarioActionResultType: ScenarioActionResultType.ValidationError,
                        msg: msg.toString(),
                    };
                }
            });
    }

    async addComment(processName: string, versionId: number, comment: string): Promise<ResponseStatus> {
        try {
            await api.post(`/processes/${encodeURIComponent(processName)}/${versionId}/activity/comment`, comment);
            this.#addInfo(i18next.t("notification.info.commentAdded", "Comment added"));
            return { status: "success" };
        } catch (error) {
            await this.#addError(i18next.t("notification.error.failedToAddComment", "Failed to add comment"), error);
            return { status: "error", error };
        }
    }

    async updateComment(processName: string, comment: string, scenarioActivityId: string): Promise<ResponseStatus> {
        try {
            await api.put(`/processes/${encodeURIComponent(processName)}/activity/comment/${scenarioActivityId}`, comment);
            this.#addInfo(i18next.t("notification.info.commentModified", "Comment modified"));
            return { status: "success" };
        } catch (error) {
            if (error?.response?.status != 400) {
                await this.#addError(i18next.t("notification.error.failedToAddComment", "Failed to add comment"), error);
            }
            return { status: "error", error };
        }
    }

    async deleteActivityComment(processName: string, scenarioActivityId: string): Promise<ResponseStatus> {
        try {
            await api.delete(`/processes/${encodeURIComponent(processName)}/activity/comment/${scenarioActivityId}`);
            this.#addInfo(i18next.t("notification.info.commendDeleted", "Comment deleted"));
            return { status: "success" };
        } catch (error) {
            await this.#addError(i18next.t("notification.error.failedToDeleteComment", "Failed to delete comment"), error);
            return { status: "error", error };
        }
    }

    async addAttachment(processName: ProcessName, versionId: ProcessVersionId, file: File): Promise<ResponseStatus> {
        try {
            await api.post(`/processes/${encodeURIComponent(processName)}/${versionId}/activity/attachments`, file, {
                headers: { "Content-Disposition": `attachment; filename="${file.name}"` },
            });
            this.#addInfo(i18next.t("notification.error.attachmentAdded", "Attachment added"));
            return { status: "success" };
        } catch (error) {
            await this.#addError(i18next.t("notification.error.failedToAddAttachment", "Failed to add attachment"), error, true);
            return { status: "error", error };
        }
    }

    downloadAttachment(processName: ProcessName, attachmentId: string, fileName: string) {
        return api
            .get(`/processes/${encodeURIComponent(processName)}/activity/attachments/${attachmentId}`, {
                responseType: "blob",
            })
            .then((response) => FileSaver.saveAs(response.data, fileName))
            .catch((error) =>
                this.#addError(i18next.t("notification.error.failedToDownloadAttachment", "Failed to download attachment"), error),
            );
    }

    async deleteAttachment(processName: ProcessName, attachmentId: string): Promise<ResponseStatus> {
        try {
            await api.delete(`/processes/${encodeURIComponent(processName)}/activity/attachments/${attachmentId}`);

            return { status: "success" };
        } catch (error) {
            await this.#addError(i18next.t("notification.error.failedToDeleteAttachment", "Failed to delete attachment"), error);
            return { status: "error", error };
        }
    }

    changeProcessName(processName, newProcessName): Promise<boolean> {
        const failedToChangeNameMessage = i18next.t("notification.error.failedToChangeName", "Failed to change scenario name");
        if (newProcessName == null || newProcessName === "") {
            this.#addErrorMessage(failedToChangeNameMessage, i18next.t("notification.error.newNameEmpty", "Name cannot be empty"), true);
            return Promise.resolve(false);
        }

        return api
            .put(`/processes/${encodeURIComponent(processName)}/rename/${encodeURIComponent(newProcessName)}`)
            .then(() => {
                this.#addInfo(i18next.t("notification.error.nameChanged", "Scenario name changed"));
                return true;
            })
            .catch(async (error) => {
                await this.#addError(failedToChangeNameMessage, error, true);
                return false;
            });
    }

    exportProcess(processName, scenarioGraph: ScenarioGraph, versionId: number) {
        return api
            .post(`/processesExport/${encodeURIComponent(processName)}`, this.#sanitizeScenarioGraph(scenarioGraph), {
                responseType: "blob",
            })
            .then((response) => FileSaver.saveAs(response.data, `${processName}-${versionId}.json`))
            .catch((error) => this.#addError(i18next.t("notification.error.failedToExport", "Failed to export"), error));
    }

    exportProcessToPdf(processName, versionId, data) {
        return api
            .post(`/processesExport/pdf/${encodeURIComponent(processName)}/${versionId}`, data, { responseType: "blob" })
            .then((response) => FileSaver.saveAs(response.data, `${processName}-${versionId}.pdf`))
            .catch((error) => this.#addError(i18next.t("notification.error.failedToExportPdf", "Failed to export PDF"), error));
    }

    validateProcess(processName: string, unsavedOrCurrentName: string, scenarioGraph: ScenarioGraph, controller?: AbortController) {
        const request = {
            processName: unsavedOrCurrentName,
            scenarioGraph: this.#sanitizeScenarioGraph(scenarioGraph),
        };
        return api
            .post<ValidationResult>(`/processValidation/${encodeURIComponent(processName)}`, request, {
                signal: controller?.signal,
            })
            .catch((error) => {
                if (axios.isCancel(error)) return Promise.reject(error);
                this.#addError(i18next.t("notification.error.fatalValidationError", "Fatal validation error, cannot save"), error, true);
                // to prevent closing edit node modal and corrupting graph display
                return Promise.reject(error);
            });
    }

    validateNode(processName: string, node: ValidationRequest): Promise<ValidationData | void> {
        return api
            .post(`/nodes/${encodeURIComponent(processName)}/validation`, node)
            .then((res) => res.data)
            .catch((error) => {
                this.#addError(i18next.t("notification.error.failedToValidateNode", "Failed to get node validation"), error, true);
                return;
            });
    }

    validateGenericActionParameters(
        processingType: string,
        validationRequest: GenericValidationRequest,
    ): Promise<AxiosResponse<ValidationData>> {
        const promise = api.post(`/parameters/${encodeURIComponent(processingType)}/validate`, validationRequest);
        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.failedToValidateGenericParameters", "Failed to validate parameters"), error, true),
        );
        return promise;
    }

    validateAdhocTestParameters(
        scenarioName: string,
        sourceParameters: SourceWithParametersTest,
        scenarioGraph: ScenarioGraph,
    ): Promise<AxiosResponse<ValidationData>> {
        const validationRequest: TestAdhocValidationRequest = {
            testData: {
                type: "WITH_PARAMETERS",
                sourceParameters: sourceParameters,
            },
            scenarioGraph: this.#sanitizeScenarioGraph(scenarioGraph),
        };
        const promise = api.post(`/scenarioTesting/${encodeURIComponent(scenarioName)}/validate`, validationRequest);
        promise.catch((error) =>
            this.#addError(
                i18next.t("notification.error.failedToValidateAdhocTestParameters", "Failed to validate parameters"),
                error,
                true,
            ),
        );
        return promise;
    }

    validateScenarioLabels(labels: string[]): Promise<AxiosResponse<ScenarioLabelsValidationResponse>> {
        const data = { labels: labels };
        return api
            .post<ScenarioLabelsValidationResponse>(`/scenarioLabels/validation`, data)
            .catch((error) =>
                Promise.reject(
                    this.#addError(i18next.t("notification.error.cannotValidateScenarioLabels", "Cannot validate scenario labels"), error),
                ),
            );
    }

    validateProcessVersion(processName: string, localVersion: number): Promise<AxiosResponse<ProcessVersionValidationResponse>> {
        const data = { localVersion: localVersion };
        return api
            .post<ProcessVersionValidationResponse>(`/versionControl/${processName}/versionValidation`, data)
            .catch((error) =>
                Promise.reject(
                    this.#addError(i18next.t("notification.error.cannotValidateProcessVersion", "Cannot validate process version"), error),
                ),
            );
    }

    getExpressionSuggestions(processingType: string, request: ExpressionSuggestionRequest): Promise<AxiosResponse<ExpressionSuggestion[]>> {
        const url = `/parameters/${encodeURIComponent(processingType)}/suggestions`;
        const promise = api.post<ExpressionSuggestion[]>(url, request);
        promise.catch((error) =>
            this.#addError(
                i18next.t("notification.error.failedToFetchExpressionSuggestions", "Failed to get expression suggestions"),
                error,
                true,
            ),
        );

        const expr = request.expression.expression;
        if (!expr.endsWith(".")) {
            return promise;
        }

        const bracketRequest: ExpressionSuggestionRequest = {
            ...request,
            expression: {
                ...request.expression,
                expression: expr.slice(0, -1) + '[""]',
            },
            caretPosition2d: {
                ...request.caretPosition2d,
                column: request.caretPosition2d.column + 1,
            },
        };

        const bracketPromise = api.post<ExpressionSuggestion[]>(url, bracketRequest).catch(() => null);

        return Promise.all([promise, bracketPromise]).then(([dotResponse, bracketResponse]) => {
            if (!bracketResponse) return dotResponse;

            const existingNames = new Set(dotResponse.data.map((s) => s.methodName));
            const additional = bracketResponse.data
                .filter((s) => !existingNames.has(s.methodName))
                .map((s) => ({ ...s, bracketAccess: true }));
            return {
                ...dotResponse,
                data: [...dotResponse.data, ...additional],
            };
        });
    }

    validateProperties(
        processName: string,
        { name = processName, additionalFields }: PropertiesValidationRequest,
    ): Promise<ValidationData | void> {
        return api
            .post(`/properties/${encodeURIComponent(processName)}/validation`, { name, additionalFields })
            .then((res) => res.data)
            .catch((error) => {
                this.#addError(
                    i18next.t("notification.error.failedToValidateProperties", "Failed to get properties validation"),
                    error,
                    true,
                );
                return;
            });
    }

    getNodeAdditionalInfo(processName: string, node: NodeType, controller?: AbortController): Promise<AdditionalInfo | null> {
        return api
            .post<AdditionalInfo>(`/nodes/${encodeURIComponent(processName)}/additionalInfo`, node, {
                signal: controller?.signal,
            })
            .then((res) => res.data)
            .catch((error) => {
                this.#addError(
                    i18next.t("notification.error.failedToFetchNodeAdditionalInfo", "Failed to get node additional info"),
                    error,
                    true,
                );
                return null;
            });
    }

    getPropertiesAdditionalInfo(
        processName: string,
        processProperties: PropertiesType,
        controller?: AbortController,
    ): Promise<AdditionalInfo | null> {
        return api
            .post<AdditionalInfo>(`/properties/${encodeURIComponent(processName)}/additionalInfo`, processProperties, {
                signal: controller?.signal,
            })
            .then((res) => res.data)
            .catch((error) => {
                this.#addError(
                    i18next.t("notification.error.failedToFetchPropertiesAdditionalInfo", "Failed to get properties additional info"),
                    error,
                    true,
                );
                return null;
            });
    }

    fetchTestCaseNodeAdditionalVariables(
        processingType: string,
        request: TestCaseNodeAdditionalVariablesRequest,
    ): Promise<TestCaseNodeAdditionalVariablesResponse | null> {
        return api
            .post(`/parameters/${encodeURIComponent(processingType)}/testCase/additionalVariables`, request)
            .then((res) => res.data)
            .catch((error) => {
                this.#addError(
                    i18next.t(
                        "notification.error.failedToFetchTestCaseNodeAdditionalVariables",
                        "Failed to fetch test case node additional variables",
                    ),
                    error,
                    true,
                );
                return null;
            });
    }

    generateEnricherMockExpression(
        scenarioName: string,
        payload: { variableTypes: VariableTypes; processProperties: PropertiesType; enricher: NodeType },
    ): Promise<{ enricherMockExpression?: ExpressionObj } | null> {
        return api
            .post(`/nodes/${encodeURIComponent(scenarioName)}/testCase/enricherMock`, payload)
            .then((res) => res.data)
            .catch((error) => {
                this.#addError(
                    i18next.t("notification.error.failedToGenerateEnricherMockExpression", "Failed to generate enricher mock expression"),
                    error,
                    true,
                );
                return null;
            });
    }

    //This method will return *FAILED* promise if validation fails with e.g. 400 (fatal validation error)

    getTestCapabilities(processName: string, scenarioGraph: ScenarioGraph) {
        const promise = api.post(
            `/scenarioTesting/${encodeURIComponent(processName)}/capabilities`,
            this.#sanitizeScenarioGraph(scenarioGraph),
        );
        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.failedToGetCapabilities", "Failed to get capabilities"), error, true),
        );
        return promise;
    }

    getActionParameters(processName: string) {
        const promise = api.get(`/actionInfo/${encodeURIComponent(processName)}/parameters`);
        promise.catch((error) =>
            this.#addError(
                i18next.t("notification.error.failedToGetActionParameters", "Failed to get action parameters definition"),
                error,
                true,
            ),
        );
        return promise;
    }

    generateTestData(processName: string, scenarioGraph: ScenarioGraph, numberOfSamples: number): Promise<AxiosResponse> {
        const promise = api.post(
            `/scenarioTesting/${encodeURIComponent(processName)}/generatedTestData`,
            {
                scenarioGraph: this.#sanitizeScenarioGraph(scenarioGraph),
                numberOfSamples,
            },
            {
                responseType: "blob",
            },
        );
        promise
            .then((response) => FileSaver.saveAs(response.data, `${processName}-testData`))
            .catch((error: AxiosError) =>
                this.#addError(
                    i18next.t("notification.error.failedToGenerateTestData", "Failed to generate test data due to: {{axiosError}}", {
                        axiosError: handleAxiosError(error),
                    }),
                    error,
                    true,
                ),
            );
        return promise;
    }

    fetchProcessCounts(processName: string, dateFrom: Moment, dateTo: Moment): Promise<AxiosResponse<ProcessCounts>> {
        //we use offset date time instead of timestamp to pass info about user time zone to BE
        const format = (date: Moment) => date?.format("YYYY-MM-DDTHH:mm:ssZ");

        const data = {
            dateFrom: format(dateFrom),
            dateTo: format(dateTo),
        };
        const promise = api.get(`/processCounts/${encodeURIComponent(processName)}`, { params: data });

        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.failedToFetchCounts", "Cannot fetch process counts"), error, true),
        );
        return promise;
    }

    fetchProcessLiveData(
        processName: string,
        showErrors: boolean,
        controller?: AbortController,
    ): Promise<AxiosResponse<ResultsWithCountsDto>> {
        return api
            .get<ResultsWithCountsDto>(`/liveData/${encodeURIComponent(processName)}`, { signal: controller?.signal })
            .catch((error) => {
                if (axios.isAxiosError(error) && error.response) {
                    const status = error.response.status;
                    if (showErrors) {
                        if (status === 422) {
                            this.#addError(
                                i18next.t("notification.error.liveDataNotSupported", "Live data is not supported for this scenario"),
                                error,
                                true,
                            );
                        } else {
                            this.#addError(i18next.t("notification.error.failedToFetchLiveData", "Cannot fetch live data"), error, true);
                        }
                    }
                }
                throw error;
            });
    }

    //to prevent closing edit node modal and corrupting graph display
    saveProcess(processName: ProcessName, scenarioGraph: ScenarioGraph, comment: string, labels: string[]) {
        const data = {
            scenarioGraph: this.#sanitizeScenarioGraph(scenarioGraph),
            comment: comment,
            scenarioLabels: labels,
        };
        return api.put(`/processes/${encodeURIComponent(processName)}`, data).catch((error) => {
            this.#addError(i18next.t("notification.error.failedToSave", "Failed to save"), error, true);
            return Promise.reject(error);
        });
    }

    archiveProcess(processName) {
        const promise = api.post(`/archive/${encodeURIComponent(processName)}`);
        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.failedToArchive", "Failed to archive scenario"), error, true),
        );
        return promise;
    }

    unArchiveProcess(processName) {
        return api
            .post(`/unarchive/${encodeURIComponent(processName)}`)
            .catch((error) =>
                this.#addError(i18next.t("notification.error.failedToUnArchive", "Failed to unarchive scenario"), error, true),
            );
    }

    //This method will return *FAILED* promise if save/validation fails with e.g. 400 (fatal validation error)

    createProcess(data: { name: string; category: string; isFragment: boolean; processingMode: string; engineSetupName: string }) {
        const promise = api.post(`/processes`, data);
        promise.catch((error) => {
            if (error?.response?.status != 400)
                this.#addError(i18next.t("notification.error.failedToCreate", "Failed to create scenario:"), error, true);
        });
        return promise;
    }

    importScenario(scenarioName: ProcessName, file: File) {
        const data = new FormData();
        data.append("process", file);

        const promise = api.post<Pick<Scenario, "scenarioGraph" | "validationResult">>(
            `/processes/import/${encodeURIComponent(scenarioName)}`,
            data,
        );
        promise.catch((error) => {
            this.#addError(i18next.t("notification.error.failedToImport", "Failed to import"), error, true);
        });
        return promise;
    }

    testScenarioWithFile(processName: ProcessName, scenarioGraph: ScenarioGraph, file: File) {
        const sanitized = this.#sanitizeScenarioGraph(scenarioGraph);

        const data = new FormData();
        data.append("testData", file);
        data.append("scenarioGraph", new Blob([JSON.stringify(sanitized)], { type: "application/json" }));

        const promise = api.post<ResultsWithCountsDto>(`/scenarioTesting/${encodeURIComponent(processName)}/performTest`, data, {
            params: {
                skipResultsPerTransition: this.#skipResultsPerTransition,
            },
        });
        promise.catch((error: AxiosError) =>
            this.#addError(
                i18next.t("notification.error.failedToTest", "Failed to test due to: {{axiosError}}", {
                    axiosError: handleAxiosError(error),
                }),
                error,
                true,
            ),
        );
        return promise;
    }

    testScenarioWithTestCase(scenarioName: ProcessName, scenarioGraph: ScenarioGraph, testCase: TestCase) {
        const sanitized = this.#sanitizeScenarioGraph(scenarioGraph);
        let sanitizedInputDataRecords: TestingDataRecords;
        try {
            sanitizedInputDataRecords = JSON.parse(testCase.inputs).map(mapInputDataRecordsToRunTestsFormat);
        } catch (error) {
            this.#addError(
                i18next.t("notification.error.invalidTestCaseInputsJson", "Failed to test: invalid JSON in test case inputs"),
                error,
                true,
            );
            return Promise.reject(error);
        }

        const promise = api.post<ResultsWithCountsDto>(
            `/scenarioTesting/${encodeURIComponent(scenarioName)}/performTestCase`,
            {
                scenarioGraph: sanitized,
                testCase: { ...testCase, inputs: JSON.stringify(sanitizedInputDataRecords) },
            },
            {
                params: {
                    skipResultsPerTransition: this.#skipResultsPerTransition,
                },
            },
        );
        promise.catch((error: AxiosError) =>
            this.#addError(
                i18next.t("notification.error.failedToTestScenarioWithEventsData", "Failed to test due to: {{axiosError}}", {
                    axiosError: handleAxiosError(error),
                }),
                error,
                true,
            ),
        );
        return promise;
    }

    validateTestDataWithDataRecords(scenarioName: ProcessName, scenarioGraph: ScenarioGraph, dataRecords: TestingDataRecords) {
        const sanitizedScenarioGraph = this.#sanitizeScenarioGraph(scenarioGraph);

        const promise = api.post<GenericValidationData>(`/scenarioTesting/${encodeURIComponent(scenarioName)}/validate`, {
            scenarioGraph: sanitizedScenarioGraph,
            testData: {
                type: "WITH_PARAMETERS",
                sourceParameters: {
                    sourceId: dataRecords.sourceId,
                    parameterExpressions: {
                        "Input variables": {
                            language: "json",
                            expression: dataRecords.variables,
                        },
                    },
                },
            },
        });

        promise.catch((error: AxiosError) =>
            this.#addError(
                i18next.t("notification.error.failedValidateTestDataWithEventsData", "Failed to validate due to: {{axiosError}}", {
                    axiosError: handleAxiosError(error),
                }),
                error,
                true,
            ),
        );
        return promise;
    }

    fetchTestData(scenarioName: ProcessName, scenarioGraph: ScenarioGraph, numberOfSamples = 10) {
        const sanitizedScenarioGraph = this.#sanitizeScenarioGraph(scenarioGraph);
        const promise = api.post<TestingDataRecords[]>(`/scenarioTesting/${encodeURIComponent(scenarioName)}/generatedTestData`, {
            scenarioGraph: sanitizedScenarioGraph,
            numberOfSamples,
        });

        promise.catch((error: AxiosError) =>
            this.#addError(
                i18next.t("notification.error.failedToFetchTestData", "Failed to fetch test data due to: {{axiosError}}", {
                    axiosError: handleAxiosError(error),
                }),
                error,
                true,
            ),
        );
        return promise;
    }

    fetchTestDataForSingleSource(scenarioName: ProcessName, scenarioProperties: PropertiesType, nodeData: NodeType, numberOfSamples = 10) {
        const promise = api.post<TestingDataRecords[]>(`/nodes/${encodeURIComponent(scenarioName)}/records?limit=${numberOfSamples}`, {
            processProperties: scenarioProperties,
            nodeData,
        });

        promise.catch((error: AxiosError) =>
            this.#addError(
                i18next.t(
                    "notification.error.failedToFetchTestDataForSingleSource",
                    "Failed to fetch test data for a single source due to: {{axiosError}}",
                    {
                        axiosError: handleAxiosError(error),
                    },
                ),
                error,
                true,
            ),
        );
        return promise;
    }

    testScenario(
        processName: string,
        scenarioGraph: ScenarioGraph,
        testData:
            | {
                  type: "WITH_PARAMETERS";
                  sourceParameters: SourceWithParametersTest;
              }
            | {
                  type: "WITH_LIVE_DATA";
                  numberOfSamples: number;
              },
    ) {
        const sanitized = this.#sanitizeScenarioGraph(scenarioGraph);
        const promise = api.post<ResultsWithCountsDto>(
            `/scenarioTesting/${encodeURIComponent(processName)}/performTest`,
            {
                testData,
                scenarioGraph: sanitized,
            },
            {
                params: {
                    skipResultsPerTransition: this.#skipResultsPerTransition,
                },
            },
        );
        promise.catch((error: AxiosError) =>
            this.#addError(
                i18next.t("notification.error.failedToTest", "Failed to test due to: {{axiosError}}", {
                    axiosError: handleAxiosError(error),
                }),
                error,
                true,
            ),
        );
        return promise;
    }

    compareProcesses(processName: ProcessName, thisVersion, otherVersion, remoteEnv) {
        const path = remoteEnv ? "remoteEnvironment" : "processes";

        const promise = api.get(`/${path}/${encodeURIComponent(processName)}/${thisVersion}/compare/${otherVersion}`);
        promise.catch((error) => this.#addError(i18next.t("notification.error.cannotCompare", "Cannot compare scenarios"), error, true));
        return promise;
    }

    fetchRemoteVersions(processName: ProcessName) {
        const promise = api.get(`/remoteEnvironment/${encodeURIComponent(processName)}/versions`);
        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.failedToGetVersions", "Failed to get versions from second environment"), error),
        );
        return promise;
    }

    migrateProcess(processName: ProcessName, versionId: number) {
        return api
            .post(`/remoteEnvironment/${encodeURIComponent(processName)}/${versionId}/migrate`)
            .then(() =>
                this.#addInfo(
                    i18next.t("notification.info.scenarioMigrated", "Scenario {{processName}} was migrated", {
                        processName,
                    }),
                ),
            )
            .catch((error) =>
                this.#addError(
                    i18next.t("notification.error.failedToMigrate", "Failed to migrate: {{ cause }}", { cause: error.response.data }),
                    error,
                    true,
                ),
            );
    }

    fetchOAuth2AccessToken<T>(provider: string, authorizeCode: string | string[], redirectUri: string | null) {
        return api.get<T>(
            `/authentication/${provider.toLowerCase()}?code=${authorizeCode}${redirectUri ? `&redirect_uri=${redirectUri}` : ""}`,
        );
    }

    fetchAuthenticationSettings(authenticationProvider: string) {
        return api.get<AuthenticationSettings>(`/authentication/${authenticationProvider.toLowerCase()}/settings`);
    }

    fetchScenarioParametersCombinations() {
        return api.get<ScenarioParametersCombinations>(`/scenarioParametersCombinations`);
    }

    fetchProcessDefinitionDataDict(processingType: ProcessingType, dictId: string, label: string) {
        return api
            .get<ProcessDefinitionDataDictOption[]>(`/processDefinitionData/${processingType}/dicts/${dictId}/entry?label=${label}`)
            .catch((error) =>
                Promise.reject(
                    this.#addError(
                        i18next.t("notification.error.failedToFetchProcessDefinitionDataDict", "Failed to fetch options"),
                        error,
                    ),
                ),
            );
    }

    async fetchProcessDefinitionDataDictByKey(processingType: ProcessingType, dictId: string, key: string): Promise<ResponseStatus> {
        try {
            const { data } = await api.get<ProcessDefinitionDataDictOption>(
                `/processDefinitionData/${processingType}/dicts/${dictId}/entryByKey?key=${key}`,
            );
            return { status: "success", data };
        } catch (error) {
            await this.#addError(i18next.t("notification.error.failedToFetchProcessDefinitionDataDict", "Failed to fetch options"), error);
            return { status: "error", error };
        }
    }

    fetchAllProcessDefinitionDataDicts(processingType: ProcessingType, refClazzName: string, type = "TypedClass") {
        return api
            .post<DictOption[]>(`/processDefinitionData/${processingType}/dicts`, {
                expectedType: { type: type, refClazzName, params: [] },
            })
            .catch((error) =>
                Promise.reject(
                    this.#addError(
                        i18next.t("notification.error.failedToFetchProcessDefinitionDataDict", "Failed to fetch presets"),
                        error,
                    ),
                ),
            );
    }

    fetchStatisticUsage() {
        return api.get<{
            urls: string[];
        }>(`/statistic/usage`);
    }

    sendStatistics(
        statistics: {
            name: `${EventTrackingType}_${EventTrackingSelectorType}`;
        }[],
    ) {
        return api.post(`/statistic`, { statistics });
    }

    fetchActivitiesMetadata(scenarioName: string) {
        return api.get<ActivityMetadataResponse>(`/processes/${scenarioName}/activity/activities/metadata`);
    }

    fetchActivities(scenarioName: string) {
        return api.get<ActivitiesResponse>(`/processes/${scenarioName}/activity/activities`);
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    sendChatMessage({ type, ...body }: ChatRequest, abortSignal: AbortSignal) {
        const headers = {
            "Content-Type": "application/json",
            Accept: "text/event-stream",
        };

        const PATHNAME = "custom/assistant/chat";

        /**
         * Axios doesn't support stream response, even with fetch adapter, there are problems in safari https://github.com/axios/axios/issues/5806
         */
        return fetch(
            `${API_URL}/${PATHNAME}`,
            SystemUtils.setAuthHeader({
                method: "POST",
                headers,
                body: JSON.stringify(body),
                signal: abortSignal,
            }),
        );
    }

    async nodeActions(
        scenarioName: string,
        actionName: "send-sample-request" | "generate-endpoint" | "test-ws-forwarder",
        nodeData: NodeType,
    ): Promise<
        | { result: { topic: Expression; actionName: "GENERATE_ENDPOINT" } }
        | { result: { actionName: "SEND_SAMPLE_REQUEST" } }
        | { result: { ok: boolean; remoteStatus?: number; message: string; firstMsg?: string; actionName: "WS_FORWARDER_TEST_RESULT" } }
    > {
        try {
            const response = await api.post(`/custom/nodes/${scenarioName}/actions`, { actionName, nodeData });
            return response.data;
        } catch (error) {
            return await Promise.reject(
                this.#addError(
                    i18next.t("notification.error.failedToSendNodeAction", "Failed to send {{actionName}} action", { actionName }),
                    error,
                ),
            );
        }
    }

    #addInfo(message: string) {
        if (this.#notificationActions) {
            this.#notificationActions.success(message);
        }
    }

    #addErrorMessage(message: string, error: string, showErrorText: boolean) {
        if (this.#notificationActions) {
            this.#notificationActions.error(message, error, showErrorText);
        }
    }

    async #addError(message: string, error?: AxiosError<unknown>, showErrorText = false) {
        console.warn(message, error);

        if (this.#requestCanceled(error)) {
            return;
        }

        const errorResponseData = error?.response?.data;
        const errorMessage =
            errorResponseData instanceof Blob
                ? await errorResponseData.text()
                : typeof errorResponseData === "string"
                ? errorResponseData
                : JSON.stringify(errorResponseData);

        this.#addErrorMessage(message, errorMessage, showErrorText);
        return Promise.resolve(error);
    }

    #sanitizeScenarioGraph(scenarioGraph: ScenarioGraph) {
        const nodeStickyNoteSortedGraph = extractStickyNotesFromNodes(scenarioGraph);
        return withoutHackOfEmptyEdges(nodeStickyNoteSortedGraph);
    }

    #requestCanceled(error: AxiosError<unknown>) {
        return error.message === "canceled";
    }
}
