/* eslint-disable i18next/no-literal-string */
import { AxiosError, AxiosResponse } from "axios";
import FileSaver from "file-saver";
import i18next from "i18next";
import { Moment } from "moment";
import { Position, ProcessingType, SettingsData, ValidationData, ValidationRequest } from "../actions/nk";
import { GenericValidationRequest, TestAdhocValidationRequest } from "../actions/nk/adhocTesting";
import api from "../api";
import { UserData } from "../common/models/User";
import { TestResults } from "../common/TestResultUtils";
import { withoutHackOfEmptyEdges } from "../components/graph/GraphPartialsInTS/EdgeUtils";
import { CaretPosition2d, ExpressionSuggestion } from "../components/graph/node-modal/editors/expression/ExpressionSuggester";
import { AdditionalInfo } from "../components/graph/node-modal/NodeAdditionalInfoBox";
import { AvailableScenarioLabels, ScenarioLabelsValidationResponse } from "../components/Labels/types";
import {
    ActionName,
    PredefinedActionName,
    ProcessActionType,
    ProcessName,
    ProcessStateType,
    ProcessVersionId,
    Scenario,
    StatusDefinitionType,
} from "../components/Process/types";
import {
    ActivitiesResponse,
    ActivityMetadataResponse,
    ActivityType,
    ActivityTypesRelatedToExecutions,
} from "../components/toolbars/activities/types";
import { ToolbarsConfig } from "../components/toolbarSettings/types";
import { EventTrackingSelectorType, EventTrackingType } from "../containers/event-tracking";
import { BackendNotification } from "../containers/Notifications";
import { ProcessCounts } from "../reducers/graph";
import { AuthenticationSettings } from "../reducers/settings";
import { Expression, LayoutData, NodeType, ProcessAdditionalFields, ProcessDefinitionData, ScenarioGraph, VariableTypes } from "../types";
import { Instant, WithId } from "../types/common";
import { fixAggregateParameters, fixBranchParametersTemplate } from "./parametersUtils";
import { Dimensions, StickyNote } from "../common/StickyNote";
import { STICKY_NOTE_DEFAULT_COLOR } from "../components/graph/EspNode/stickyNote";

type HealthCheckProcessDeploymentType = {
    status: string;
    message: null | string;
    processes: null | Array<string>;
};

export type HealthCheckResponse = {
    state: HealthState;
    error?: string;
    processes?: string[];
};

export enum HealthState {
    ok = "ok",
    error = "error",
}

export type FetchProcessQueryParams = Partial<{
    search: string;
    categories: string;
    isFragment: boolean;
    isArchived: boolean;
    isDeployed: boolean;
}>;

export type StatusesType = Record<Scenario["name"], ProcessStateType>;

export interface AppBuildInfo {
    name: string;
    gitCommit: string;
    buildTime: string;
    version: string;
    processingType: any;
}

export type ComponentActionType = {
    id: string;
    title: string;
    icon: string;
    url?: string;
};

export type ComponentType = {
    id: string;
    name: string;
    icon: string;
    componentType: string;
    componentGroupName: string;
    categories: string[];
    actions: ComponentActionType[];
    usageCount: number;
    allowedProcessingModes: ProcessingMode[];
    links: Array<{
        id: string;
        title: string;
        icon: string;
        url: string;
    }>;
};

export type SourceWithParametersTest = {
    sourceId: string;
    parameterExpressions: {
        [paramName: string]: Expression;
    };
};

export type NodeUsageData = {
    fragmentNodeId?: string;
    nodeId: string;
    type: string;
};

export type ComponentUsageType = {
    name: string;
    nodesUsagesData: NodeUsageData[];
    isArchived: boolean;
    isFragment: boolean;
    processCategory: string;
    modificationDate: Instant;
    modifiedBy: string;
    createdAt: Instant;
    createdBy: string;
    lastAction: ProcessActionType;
};

export type NotificationActions = {
    success(message: string): void;
    error(message: string, error: string, showErrorText: boolean): void;
    warn(message: string): void;
};

export interface TestProcessResponse {
    results: TestResults;
    counts: ProcessCounts;
}

export interface PropertiesValidationRequest {
    name: string;
    additionalFields: ProcessAdditionalFields;
}

export interface ExpressionSuggestionRequest {
    expression: Expression;
    caretPosition2d: CaretPosition2d;
    variableTypes: VariableTypes;
}

export enum ProcessingMode {
    "streaming" = "Unbounded-Stream",
    "requestResponse" = "Request-Response",
    "batch" = "Bounded-Stream",
}

export interface ScenarioParametersCombination {
    processingMode: ProcessingMode;
    category: string;
    engineSetupName: string;
}

export interface ScenarioParametersCombinations {
    combinations: ScenarioParametersCombination[];
    engineSetupErrors: Record<string, string[]>;
}

export type ProcessDefinitionDataDictOption = {
    key: string;
    label: string;
};
type DictOption = {
    id: string;
    label: string;
};

type ResponseStatus = { status: "success" } | { status: "error"; error: AxiosError<string> };

class HttpService {
    //TODO: Move show information about error to another place. HttpService should avoid only action (get / post / etc..) - handling errors should be in another place.
    #notificationActions: NotificationActions = null;

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

    fetchProcessDetails(processName: ProcessName, versionId?: ProcessVersionId): Promise<AxiosResponse<Scenario>> {
        const id = encodeURIComponent(processName);
        const url = versionId ? `/processes/${id}/${versionId}` : `/processes/${id}`;
        return api.get<Scenario>(url);
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
        const promise = api.get(`/processes/${encodeURIComponent(processName)}/status?currentlyPresentedVersionId=${processVersionId}`);
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

    deploy(
        processName: string,
        comment?: string,
    ): Promise<{
        isSuccess: boolean;
    }> {
        return api
            .post(`/processManagement/deploy/${encodeURIComponent(processName)}`, comment)
            .then(() => {
                return { isSuccess: true };
            })
            .catch((error) => {
                if (error?.response?.status != 400) {
                    return this.#addError(
                        i18next.t("notification.error.failedToDeploy", "Failed to deploy {{processName}}", { processName }),
                        error,
                        true,
                    ).then(() => {
                        return { isSuccess: false };
                    });
                } else {
                    throw error;
                }
            });
    }

    runOffSchedule(processName: string, comment?: string) {
        const data = {
            comment: comment,
        };
        return api
            .post(`/processManagement/runOffSchedule/${encodeURIComponent(processName)}`, data)
            .then((res) => {
                const msg = res.data.msg;
                this.#addInfo(msg);
                return {
                    isSuccess: res.data.isSuccess,
                    msg: msg,
                };
            })
            .catch((error) => {
                const msg = error.response.data.msg || error.response.data;
                const result = {
                    isSuccess: false,
                    msg: msg,
                };
                if (error?.response?.status != 400) return this.#addError(msg, error, false).then(() => result);
                return result;
            });
    }

    cancel(processName, comment?) {
        return api.post(`/processManagement/cancel/${encodeURIComponent(processName)}`, comment).catch((error) => {
            if (error?.response?.status != 400) {
                return this.#addError(
                    i18next.t("notification.error.failedToCancel", "Failed to cancel {{processName}}", { processName }),
                    error,
                    true,
                ).then(() => {
                    return { isSuccess: false };
                });
            } else {
                throw error;
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
            .catch((error) => {
                return this.#addError(failedToChangeNameMessage, error, true).then(() => false);
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

    //to prevent closing edit node modal and corrupting graph display
    validateProcess(processName: string, unsavedOrCurrentName: string, scenarioGraph: ScenarioGraph) {
        const request = {
            processName: unsavedOrCurrentName,
            scenarioGraph: this.#sanitizeScenarioGraph(scenarioGraph),
        };
        return api.post(`/processValidation/${encodeURIComponent(processName)}`, request).catch((error) => {
            this.#addError(i18next.t("notification.error.fatalValidationError", "Fatal validation error, cannot save"), error, true);
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
            sourceParameters,
            scenarioGraph: this.#sanitizeScenarioGraph(scenarioGraph),
        };
        const promise = api.post(`/scenarioTesting/${encodeURIComponent(scenarioName)}/adhoc/validate`, validationRequest);
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

    getExpressionSuggestions(processingType: string, request: ExpressionSuggestionRequest): Promise<AxiosResponse<ExpressionSuggestion[]>> {
        const promise = api.post<ExpressionSuggestion[]>(`/parameters/${encodeURIComponent(processingType)}/suggestions`, request);
        promise.catch((error) =>
            this.#addError(
                i18next.t("notification.error.failedToFetchExpressionSuggestions", "Failed to get expression suggestions"),
                error,
                true,
            ),
        );
        return promise;
    }

    validateProperties(processName: string, propertiesRequest: PropertiesValidationRequest): Promise<ValidationData | void> {
        return api
            .post(`/properties/${encodeURIComponent(processName)}/validation`, propertiesRequest)
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
        processProperties: NodeType,
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

    getTestFormParameters(processName: string, scenarioGraph: ScenarioGraph) {
        const promise = api.post(
            `/scenarioTesting/${encodeURIComponent(processName)}/parameters`,
            this.#sanitizeScenarioGraph(scenarioGraph),
        );
        promise.catch((error) =>
            this.#addError(
                i18next.t("notification.error.failedToGetTestParameters", "Failed to get source test parameters definition"),
                error,
                true,
            ),
        );
        return promise;
    }

    generateTestData(processName: string, testSampleSize: string, scenarioGraph: ScenarioGraph): Promise<AxiosResponse> {
        const promise = api.post(
            `/scenarioTesting/${encodeURIComponent(processName)}/generate/${testSampleSize}`,
            this.#sanitizeScenarioGraph(scenarioGraph),
            {
                responseType: "blob",
            },
        );
        promise
            .then((response) => FileSaver.saveAs(response.data, `${processName}-testData`))
            .catch((error) =>
                this.#addError(i18next.t("notification.error.failedToGenerateTestData", "Failed to generate test data"), error, true),
            );
        return promise;
    }

    addStickyNote(scenarioName: string, scenarioVersionId: number, position: Position, dimensions: Dimensions) {
        const promise = api.post(`/processes/${encodeURIComponent(scenarioName)}/stickyNotes`, {
            scenarioVersionId,
            content: "",
            layoutData: position,
            color: STICKY_NOTE_DEFAULT_COLOR, //TODO add config for default sticky note color? For now this is default.
            dimensions: dimensions,
        });
        promise.catch((error) => {
            const errorMsg: string = error?.response?.data;
            this.#addError("Failed to add sticky note" + (errorMsg ? ": " + errorMsg : ""), error, true);
        });
        return promise;
    }

    deleteStickyNote(scenarioName: string, stickyNoteId: number) {
        const promise = api.delete(`/processes/${encodeURIComponent(scenarioName)}/stickyNotes/${stickyNoteId}`);
        promise.catch((error) =>
            this.#addError(
                i18next.t("notification.error.failedToDeleteStickyNote", `Failed to delete sticky note with id: ${stickyNoteId}`),
                error,
                true,
            ),
        );
        return promise;
    }

    updateStickyNote(scenarioName: string, scenarioVersionId: number, stickyNote: StickyNote) {
        const promise = api.put(`/processes/${encodeURIComponent(scenarioName)}/stickyNotes`, {
            noteId: stickyNote.noteId,
            scenarioVersionId,
            content: stickyNote.content,
            layoutData: stickyNote.layoutData,
            color: stickyNote.color,
            dimensions: stickyNote.dimensions,
        });
        promise.catch((error) => {
            const errorMsg = error?.response?.data;
            this.#addError("Failed to update sticky note" + errorMsg ? ": " + errorMsg : "", error, true);
        });
        return promise;
    }

    getStickyNotes(scenarioName: string, scenarioVersionId: number): Promise<AxiosResponse<StickyNote[]>> {
        const promise = api.get(`/processes/${encodeURIComponent(scenarioName)}/stickyNotes?scenarioVersionId=${scenarioVersionId}`);
        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.failedToGetStickyNotes", "Failed to get sticky notes"), error, true),
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

    importProcess(processName: ProcessName, file: File) {
        const data = new FormData();
        data.append("process", file);

        const promise = api.post(`/processes/import/${encodeURIComponent(processName)}`, data);
        promise.catch((error) => {
            this.#addError(i18next.t("notification.error.failedToImport", "Failed to import"), error, true);
        });
        return promise;
    }

    testProcess(processName: ProcessName, file: File, scenarioGraph: ScenarioGraph): Promise<AxiosResponse<TestProcessResponse>> {
        const sanitized = this.#sanitizeScenarioGraph(scenarioGraph);

        const data = new FormData();
        data.append("testData", file);
        data.append("scenarioGraph", new Blob([JSON.stringify(sanitized)], { type: "application/json" }));

        const promise = api.post(`/processManagement/test/${encodeURIComponent(processName)}`, data);
        promise.catch((error) => this.#addError(i18next.t("notification.error.failedToTest", "Failed to test"), error, true));
        return promise;
    }

    testProcessWithParameters(
        processName: ProcessName,
        testData: SourceWithParametersTest,
        scenarioGraph: ScenarioGraph,
    ): Promise<AxiosResponse<TestProcessResponse>> {
        const sanitized = this.#sanitizeScenarioGraph(scenarioGraph);
        const request = {
            sourceParameters: testData,
            scenarioGraph: sanitized,
        };

        const promise = api.post(`/processManagement/testWithParameters/${encodeURIComponent(processName)}`, request);
        promise.catch((error) => this.#addError(i18next.t("notification.error.failedToTest", "Failed to test"), error, true));
        return promise;
    }

    testScenarioWithGeneratedData(
        processName: ProcessName,
        testSampleSize: string,
        scenarioGraph: ScenarioGraph,
    ): Promise<AxiosResponse<TestProcessResponse>> {
        const promise = api.post(
            `/processManagement/generateAndTest/${processName}/${testSampleSize}`,
            this.#sanitizeScenarioGraph(scenarioGraph),
        );
        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.failedToGenerateAndTest", "Failed to generate and test"), error, true),
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
            .catch((error) => this.#addError(i18next.t("notification.error.failedToMigrate", "Failed to migrate"), error, true));
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
        return withoutHackOfEmptyEdges(scenarioGraph);
    }

    #requestCanceled(error: AxiosError<unknown>) {
        return error.message === "canceled";
    }
}

export default new HttpService();
