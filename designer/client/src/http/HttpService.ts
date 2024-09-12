/* eslint-disable i18next/no-literal-string */
import { AxiosError, AxiosResponse } from "axios";
import FileSaver from "file-saver";
import i18next from "i18next";
import { Moment } from "moment";
import { ProcessingType, SettingsData, ValidationData, ValidationRequest } from "../actions/nk";
import api from "../api";
import { UserData } from "../common/models/User";
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
import { ToolbarsConfig } from "../components/toolbarSettings/types";
import { AuthenticationSettings } from "../reducers/settings";
import { Expression, NodeType, ProcessAdditionalFields, ProcessDefinitionData, ScenarioGraph, VariableTypes } from "../types";
import { Instant, WithId } from "../types/common";
import { BackendNotification } from "../containers/Notifications";
import { ProcessCounts } from "../reducers/graph";
import { TestResults } from "../common/TestResultUtils";
import { AdditionalInfo } from "../components/graph/node-modal/NodeAdditionalInfoBox";
import { withoutHackOfEmptyEdges } from "../components/graph/GraphPartialsInTS/EdgeUtils";
import { CaretPosition2d, ExpressionSuggestion } from "../components/graph/node-modal/editors/expression/ExpressionSuggester";
import { GenericValidationRequest } from "../actions/nk/genericAction";
import { EventTrackingSelectorType, EventTrackingType } from "../containers/event-tracking";

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
    parameterExpressions: { [paramName: string]: Expression };
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

type NotificationActions = {
    success(message: string): void;
    error(message: string, error: string, showErrorText: boolean): void;
};

export interface TestProcessResponse {
    results: TestResults;
    counts: ProcessCounts;
}

export interface PropertiesValidationRequest {
    name: string;
    additionalFields: ProcessAdditionalFields;
}

export interface CustomActionValidationRequest {
    actionName: string;
    params: Record<string, string>;
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

export type ProcessDefinitionDataDictOption = { key: string; label: string };
type DictOption = { id: string; label: string };
type ActivityTypes =
    | "SCENARIO_CREATED"
    | "SCENARIO_ARCHIVED"
    | "SCENARIO_UNARCHIVED"
    | "SCENARIO_DEPLOYED"
    | "SCENARIO_CANCELED"
    | "SCENARIO_MODIFIED"
    | "SCENARIO_NAME_CHANGED"
    | "COMMENT_ADDED"
    | "ATTACHMENT_ADDED"
    | "CHANGED_PROCESSING_MODE"
    | "INCOMING_MIGRATION"
    | "OUTGOING_MIGRATION"
    | "PERFORMED_SINGLE_EXECUTION"
    | "PERFORMED_SCHEDULED_EXECUTION"
    | "AUTOMATIC_UPDATE";

export interface ActivityMetadata {
    type: ActivityTypes;
    displayableName: string;
    icon: string;
    supportedActions: string[];
}

export interface ActionMetadata {
    id: "compare" | "delete_comment" | "edit_comment" | "download_attachment" | "delete_attachment";
    displayableName: string;
    icon: string;
}

export interface ActivitiesResponse {
    activities: {
        id: string;
        type: ActivityTypes;
        user: string;
        date: string;
        scenarioVersionId?: number;
        comment?: string;
        overrideDisplayableName?: string;
        overrideSupportedActions?: string[];
        additionalFields: { name: string; value: string }[];
    }[];
}

export interface ActivityMetadataResponse {
    activities: ActivityMetadata[];
    actions: ActionMetadata[];
}

const activitiesMetadataMock: { activities: ActivityMetadata[]; actions: ActionMetadata[] } = {
    activities: [
        {
            type: "SCENARIO_CREATED",
            displayableName: "Scenario created",
            icon: "/assets/states/deploy.svg",
            supportedActions: [],
        },
        {
            type: "SCENARIO_ARCHIVED",
            displayableName: "Scenario archived",
            icon: "/assets/states/error.svg",
            supportedActions: [],
        },
        {
            type: "SCENARIO_UNARCHIVED",
            displayableName: "Scenario unarchived",
            icon: "/assets/states/error.svg",
            supportedActions: [],
        },
        {
            type: "SCENARIO_DEPLOYED",
            displayableName: "Deployment",
            icon: "/assets/states/error.svg",
            supportedActions: [],
        },
        {
            type: "SCENARIO_CANCELED",
            displayableName: "Cancel",
            icon: "/assets/states/error.svg",
            supportedActions: [],
        },
        {
            type: "SCENARIO_MODIFIED",
            displayableName: "New version saved",
            icon: "/assets/states/error.svg",
            supportedActions: ["compare"],
        },
        {
            type: "SCENARIO_NAME_CHANGED",
            displayableName: "Scenario name changed",
            icon: "/assets/states/error.svg",
            supportedActions: [],
        },
        {
            type: "COMMENT_ADDED",
            displayableName: "Comment",
            icon: "/assets/states/error.svg",
            supportedActions: ["delete_comment", "edit_comment"],
        },
        {
            type: "ATTACHMENT_ADDED",
            displayableName: "Attachment",
            icon: "/assets/states/error.svg",
            supportedActions: [],
        },
        {
            type: "CHANGED_PROCESSING_MODE",
            displayableName: "Processing mode change",
            icon: "/assets/states/error.svg",
            supportedActions: [],
        },
        {
            type: "INCOMING_MIGRATION",
            displayableName: "Incoming migration",
            icon: "/assets/states/error.svg",
            supportedActions: ["compare"],
        },
        {
            type: "OUTGOING_MIGRATION",
            displayableName: "Outgoing migration",
            icon: "/assets/states/error.svg",
            supportedActions: [],
        },
        {
            type: "PERFORMED_SINGLE_EXECUTION",
            displayableName: "Processing data",
            icon: "/assets/states/error.svg",
            supportedActions: [],
        },
        {
            type: "PERFORMED_SCHEDULED_EXECUTION",
            displayableName: "Processing data",
            icon: "/assets/states/error.svg",
            supportedActions: [],
        },
        {
            type: "AUTOMATIC_UPDATE",
            displayableName: "Automatic update",
            icon: "/assets/states/error.svg",
            supportedActions: ["compare"],
        },
    ],
    actions: [
        {
            id: "compare",
            displayableName: "Compare",
            icon: "/assets/states/error.svg",
        },
        {
            id: "delete_comment",
            displayableName: "Delete",
            icon: "/assets/states/error.svg",
        },
        {
            id: "edit_comment",
            displayableName: "Edit",
            icon: "/assets/states/error.svg",
        },
        {
            id: "download_attachment",
            displayableName: "Download",
            icon: "/assets/states/error.svg",
        },
        {
            id: "delete_attachment",
            displayableName: "Delete",
            icon: "/assets/states/error.svg",
        },
    ],
};

const activitiesMock: ActivitiesResponse = {
    activities: [
        {
            id: "33e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "4677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2024-01-17T13:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2024-01-17T12:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "10b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2024-01-17T11:01:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2024-01-16T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2024-01-15T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "4a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2024-01-14T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2024-01-12T13:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2024-01-11T11:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2024-01-11T12:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2024-01-11T18:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2024-01-11T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2024-01-11T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2024-01-11T01:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e8",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a5248548",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec3-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc5-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "e4f55ffb-e595-417b-8586-2c2eee99d75e",
            type: "AUTOMATIC_UPDATE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "changes",
                    value: "JIRA-12345, JIRA-32146",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successful",
                },
            ],
        },
        {
            id: "133e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "24677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "3aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "410b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "5d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "6c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "74a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "8cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "9552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f5",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c3",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb2",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e83",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a52485487",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec53-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc544-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "e4f55ffb3-e595-417b-8586-2c2eee99d75e",
            type: "AUTOMATIC_UPDATE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "changes",
                    value: "JIRA-12345, JIRA-32146",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successful",
                },
            ],
        },
        {
            id: "10b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "4a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "33e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "4677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "10b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "4a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e8",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a5248548",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec3-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc5-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "e4f55ffb-e595-417b-8586-2c2eee99d75e",
            type: "AUTOMATIC_UPDATE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "changes",
                    value: "JIRA-12345, JIRA-32146",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successful",
                },
            ],
        },
        {
            id: "133e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "24677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "3aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "410b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "5d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "6c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "74a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "8cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "9552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f5",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c3",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb2",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e83",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a52485487",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec53-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc544-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "33e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "4677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "10b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "4a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e8",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a5248548",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec3-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc5-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "e4f55ffb-e595-417b-8586-2c2eee99d75e",
            type: "AUTOMATIC_UPDATE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "changes",
                    value: "JIRA-12345, JIRA-32146",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successful",
                },
            ],
        },
        {
            id: "133e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "24677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "3aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "410b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "5d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "6c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "74a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "8cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "9552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f5",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c3",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb2",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e83",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a52485487",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec53-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc544-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "33e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "4677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "10b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "4a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e8",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a5248548",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec3-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc5-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "e4f55ffb-e595-417b-8586-2c2eee99d75e",
            type: "AUTOMATIC_UPDATE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "changes",
                    value: "JIRA-12345, JIRA-32146",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successful",
                },
            ],
        },
        {
            id: "133e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "24677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "3aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "410b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "5d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "6c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "74a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "8cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "9552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f5",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c3",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb2",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e83",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a52485487",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec53-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc544-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "33e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "4677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "10b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "4a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e8",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a5248548",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec3-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc5-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "e4f55ffb-e595-417b-8586-2c2eee99d75e",
            type: "AUTOMATIC_UPDATE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "changes",
                    value: "JIRA-12345, JIRA-32146",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successful",
                },
            ],
        },
        {
            id: "133e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "24677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "3aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "410b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "5d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "6c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "74a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "8cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "9552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f5",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c3",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb2",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e83",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a52485487",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec53-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc544-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "33e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "4677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "10b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "4a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e8",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a5248548",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec3-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc5-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "e4f55ffb-e595-417b-8586-2c2eee99d75e",
            type: "AUTOMATIC_UPDATE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "changes",
                    value: "JIRA-12345, JIRA-32146",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successful",
                },
            ],
        },
        {
            id: "133e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "24677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "3aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "410b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "5d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "6c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "74a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "8cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "9552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f5",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c3",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb2",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e83",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a52485487",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec53-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc544-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "33e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "4677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "10b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "4a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e8",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a5248548",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec3-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc5-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "e4f55ffb-e595-417b-8586-2c2eee99d75e",
            type: "AUTOMATIC_UPDATE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "changes",
                    value: "JIRA-12345, JIRA-32146",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successful",
                },
            ],
        },
        {
            id: "133e5143e-187d-455e-99ec-56f607729c98",
            type: "SCENARIO_CREATED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "24677ad89-154a-424d-b1d3-26632b0b6b8a",
            type: "SCENARIO_ARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "3aa4b30a8-9051-4a2a-ae6e-7042210ae096",
            type: "SCENARIO_UNARCHIVED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [],
        },
        {
            id: "410b5f550-eaef-419d-8264-219dca9a84c5",
            type: "SCENARIO_DEPLOYED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Deployment of scenario - task <a href='http://Jira-1234.com'>JIRA-1234</a>",
            additionalFields: [],
        },
        {
            id: "5d3381b4d-220e-459e-bedd-71b142c257a7",
            type: "SCENARIO_CANCELED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Canceled because marketing campaign ended",
            additionalFields: [],
        },
        {
            id: "6c21a0472-6bef-4b44-aacc-aae307546d89",
            type: "SCENARIO_MODIFIED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            comment: "Added new processing step",
            additionalFields: [],
            overrideDisplayableName: "Version 1 saved",
        },
        {
            id: "74a6805de-6555-4976-92b2-9baefcccd990",
            type: "SCENARIO_NAME_CHANGED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "oldName",
                    value: "marketing campaign",
                },
                {
                    name: "newName",
                    value: "old marketing campaign",
                },
            ],
        },
        {
            id: "8cd1eb7b8-b266-44b3-983b-e4ba0cecb44b",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Now scenario handles errors in datasource better",
            additionalFields: [],
        },
        {
            id: "9552cf846-a330-46d8-a747-5884813ea6a3",
            type: "COMMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "12540601-f4d4-43ca-a303-a19baee30f8f5",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "attachmentId",
                    value: "10000001",
                },
                {
                    name: "attachmentFilename",
                    value: "attachment01.png",
                },
            ],
        },
        {
            id: "b350c1f4-0dda-4672-a525-23697fd58a2c3",
            type: "ATTACHMENT_ADDED",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "deletedByUser",
                    value: "John Doe",
                },
            ],
            overrideSupportedActions: [],
        },
        {
            id: "4de805c3-498d-4d51-837e-6931ba58f9fb2",
            type: "CHANGED_PROCESSING_MODE",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "from",
                    value: "Request-Response",
                },
                {
                    name: "to",
                    value: "Request-Response",
                },
            ],
        },
        {
            id: "adaa9335-6ca9-4afe-9791-0bb71375f6e83",
            type: "INCOMING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration from preprod",
            additionalFields: [
                {
                    name: "sourceEnvironment",
                    value: "preprod",
                },
                {
                    name: "sourcescenarioVersionId",
                    value: "23",
                },
            ],
        },
        {
            id: "3dedd8bf-b3da-4d91-a3ea-a424a52485487",
            type: "OUTGOING_MIGRATION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            comment: "Migration to preprod",
            additionalFields: [
                {
                    name: "destinationEnvironment",
                    value: "preprod",
                },
            ],
        },
        {
            id: "44a59921-c99b-4dcd-8ec53-06739c0825e3",
            type: "PERFORMED_SINGLE_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
        {
            id: "f1bd6bc544-f624-4074-aa3d-072fda51331d",
            type: "PERFORMED_SCHEDULED_EXECUTION",
            user: "some user",
            date: "2023-01-17T14:21:17Z",
            scenarioVersionId: 1,
            additionalFields: [
                {
                    name: "params",
                    value: "Batch size=1",
                },
                {
                    name: "dateFinished",
                    value: "2023-01-17T14:21:17Z",
                },
                {
                    name: "status",
                    value: "Successfully executed",
                },
            ],
        },
    ],
};

function promiseWithTimeout<T = Record<string, unknown>>(promise, ms): Promise<T> {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve(promise);
        }, ms);
    });
}

class HttpService {
    //TODO: Move show information about error to another place. HttpService should avoid only action (get / post / etc..) - handling errors should be in another place.
    #notificationActions: NotificationActions = null;

    setNotificationActions(na: NotificationActions) {
        this.#notificationActions = na;
    }

    loadBackendNotifications(): Promise<BackendNotification[]> {
        return api.get<BackendNotification[]>("/notifications").then((d) => {
            return d.data;
        });
    }

    fetchHealthCheckProcessDeployment(): Promise<HealthCheckResponse> {
        return api
            .get("/app/healthCheck/process/deployment")
            .then(() => ({ state: HealthState.ok }))
            .catch((error) => {
                const { message, processes }: HealthCheckProcessDeploymentType = error.response?.data || {};
                return { state: HealthState.error, error: message, processes: processes };
            });
    }

    fetchSettings() {
        return api.get<SettingsData>("/settings");
    }

    fetchSettingsWithAuth(): Promise<SettingsData & { authentication: AuthenticationSettings }> {
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
        const promise = api
            .get<ProcessDefinitionData>(`/processDefinitionData/${processingType}?isFragment=${isFragment}`)
            .then((response) => {
                // This is a walk-around for having part of node template (branch parameters) outside of itself.
                // See note in DefinitionPreparer on backend side. // TODO remove it after API refactor
                response.data.componentGroups.forEach((group) => {
                    group.components.forEach((component) => {
                        component.node.branchParametersTemplate = component.branchParametersTemplate;
                    });
                });

                return response;
            });
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

    fetchProcessToolbarsConfiguration(processName) {
        const promise = api.get<WithId<ToolbarsConfig>>(`/processes/${encodeURIComponent(processName)}/toolbars`);
        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.cannotFetchToolbarConfiguration", "Cannot fetch toolbars configuration"), error),
        );
        return promise;
    }

    fetchProcessState(processName: ProcessName) {
        const promise = api.get(`/processes/${encodeURIComponent(processName)}/status`);
        promise.catch((error) => this.#addError(i18next.t("notification.error.cannotFetchStatus", "Cannot fetch status"), error));
        return promise;
    }

    fetchProcessesDeployments(processName: string) {
        return api
            .get<
                {
                    performedAt: string;
                    actionName: ActionName;
                }[]
            >(`/processes/${encodeURIComponent(processName)}/deployments`)
            .then((res) =>
                res.data.filter(({ actionName }) => actionName === PredefinedActionName.Deploy).map(({ performedAt }) => performedAt),
            );
    }

    deploy(processName: string, comment?: string): Promise<{ isSuccess: boolean }> {
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

    customAction(processName: string, actionName: string, params: Record<string, unknown>, comment?: string) {
        const data = { actionName: actionName, comment: comment, params: params };
        return api
            .post(`/processManagement/customAction/${encodeURIComponent(processName)}`, data)
            .then((res) => {
                const msg = res.data.msg;
                this.#addInfo(msg);
                return { isSuccess: res.data.isSuccess, msg: msg };
            })
            .catch((error) => {
                const msg = error.response.data.msg || error.response.data;
                const result = { isSuccess: false, msg: msg };
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

    fetchProcessActivity(processName) {
        return api.get(`/processes/${encodeURIComponent(processName)}/activity`);
    }

    addComment(processName, versionId, data) {
        return api
            .post(`/processes/${encodeURIComponent(processName)}/${versionId}/activity/comments`, data)
            .then(() => this.#addInfo(i18next.t("notification.info.commentAdded", "Comment added")))
            .catch((error) => this.#addError(i18next.t("notification.error.failedToAddComment", "Failed to add comment"), error));
    }

    deleteComment(processName, commentId) {
        return api
            .delete(`/processes/${encodeURIComponent(processName)}/activity/comments/${commentId}`)
            .then(() => this.#addInfo(i18next.t("notification.info.commendDeleted", "Comment deleted")))
            .catch((error) => this.#addError(i18next.t("notification.error.failedToDeleteComment", "Failed to delete comment"), error));
    }

    addAttachment(processName: ProcessName, versionId: ProcessVersionId, file: File) {
        return api
            .post(`/processes/${encodeURIComponent(processName)}/${versionId}/activity/attachments`, file, {
                headers: { "Content-Disposition": `attachment; filename="${file.name}"` },
            })
            .then(() => this.#addInfo(i18next.t("notification.error.attachmentAdded", "Attachment added")))
            .catch((error) =>
                this.#addError(i18next.t("notification.error.failedToAddAttachment", "Failed to add attachment"), error, true),
            );
    }

    downloadAttachment(processName: ProcessName, attachmentId, fileName: string) {
        return api
            .get(`/processes/${encodeURIComponent(processName)}/activity/attachments/${attachmentId}`, {
                responseType: "blob",
            })
            .then((response) => FileSaver.saveAs(response.data, fileName))
            .catch((error) =>
                this.#addError(i18next.t("notification.error.failedToDownloadAttachment", "Failed to download attachment"), error),
            );
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

    validateCustomAction(processName: string, customActionRequest: CustomActionValidationRequest): Promise<ValidationData> {
        return api
            .post(`/processManagement/customAction/${encodeURIComponent(processName)}/validation`, customActionRequest)
            .then((res) => res.data)
            .catch((error) => {
                this.#addError(
                    i18next.t("notification.error.failedToValidateCustomAction", "Failed to get CustomActionValidation"),
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
        const promise = api.post(`/testInfo/${encodeURIComponent(processName)}/capabilities`, this.#sanitizeScenarioGraph(scenarioGraph));
        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.failedToGetCapabilities", "Failed to get capabilities"), error, true),
        );
        return promise;
    }

    getTestFormParameters(processName: string, scenarioGraph: ScenarioGraph) {
        const promise = api.post(`/testInfo/${encodeURIComponent(processName)}/testParameters`, this.#sanitizeScenarioGraph(scenarioGraph));
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
            `/testInfo/${encodeURIComponent(processName)}/generate/${testSampleSize}`,
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

    fetchProcessCounts(processName: string, dateFrom: Moment, dateTo: Moment): Promise<AxiosResponse<ProcessCounts>> {
        //we use offset date time instead of timestamp to pass info about user time zone to BE
        const format = (date: Moment) => date?.format("YYYY-MM-DDTHH:mm:ssZ");

        const data = { dateFrom: format(dateFrom), dateTo: format(dateTo) };
        const promise = api.get(`/processCounts/${encodeURIComponent(processName)}`, { params: data });

        promise.catch((error) =>
            this.#addError(i18next.t("notification.error.failedToFetchCounts", "Cannot fetch process counts"), error, true),
        );
        return promise;
    }

    //to prevent closing edit node modal and corrupting graph display
    saveProcess(processName: ProcessName, scenarioGraph: ScenarioGraph, comment: string) {
        const data = { scenarioGraph: this.#sanitizeScenarioGraph(scenarioGraph), comment: comment };
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
                expectedType: {
                    value: { type: type, refClazzName, params: [] },
                },
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
        return api.get<{ urls: string[] }>(`/statistic/usage`);
    }

    sendStatistics(statistics: { name: `${EventTrackingType}_${EventTrackingSelectorType}` }[]) {
        return api.post(`/statistic`, { statistics });
    }

    fetchActivitiesMetadata() {
        return promiseWithTimeout<ActivityMetadataResponse>(Promise.resolve(activitiesMetadataMock), 1000);
    }

    fetchActivities() {
        return promiseWithTimeout<ActivitiesResponse>(Promise.resolve(activitiesMock), 500);
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
