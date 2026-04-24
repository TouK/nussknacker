import type { AxiosError } from "axios";

import type { CaretPosition2d } from "../../components/graph/node-modal/editors/expression/ExpressionSuggester";
import type { ProcessStateType, Scenario } from "../../components/Process/types";
import type { Instant } from "../../types/common";
import type { Expression, NodeId, NodeType, PropertiesType } from "../../types/node";
import type { ProcessAdditionalFields, ScenarioGraph } from "../../types/scenarioGraph";
import type { VariableTypes } from "../../types/validation";

export type HealthCheckProcessDeploymentType = {
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
    label: string;
};

export type SourceWithParametersTest = {
    sourceId: string;
    parameterExpressions: {
        [paramName: string]: Expression;
    };
};

export type NodesDeploymentData = Record<NodeId, Record<string, string>>;

export type ScenarioGraphSource = {
    type: ScenarioGraphSourceType;
    scenarioGraph?: ScenarioGraph;
    scenarioLabels?: string[];
    baseScenarioVersionId?: number;
};

export enum ScenarioGraphSourceType {
    FROM_GRAPH = "FromGraph",
}

export type DeployResponse = {
    deployedScenarioVersionId: number;
};

export type NodeUsageData = {
    fragmentNodeId?: string;
    fragmentNodeName?: string;
    nodeId: string;
    nodeName: string;
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
};

export type NotificationActions = {
    success(message: string): void;
    error(message: string, error: string, showErrorText: boolean): void;
    warn(message: string): void;
};

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

export type DictOption = {
    id: string;
    label: string;
};

export type ResponseStatus = { status: "success"; data?: any } | { status: "error"; error: AxiosError<string> };

export type TestCaseNodeAdditionalVariablesRequest = {
    variableTypes: VariableTypes;
    nodeData: NodeType;
    scenarioProperties: PropertiesType;
};

export type TestCaseNodeAdditionalVariablesResponse = {
    assertionsAdditionalVariables: VariableTypes;
};
