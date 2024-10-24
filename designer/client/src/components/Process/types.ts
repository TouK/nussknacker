/* eslint-disable i18next/no-literal-string */
import { UnknownRecord, Instant } from "../../types/common";
import { ScenarioGraph, ValidationResult } from "../../types";
import { ProcessingMode } from "../../http/HttpService";

export enum PredefinedActionName {
    Deploy = "DEPLOY",
    Cancel = "CANCEL",
    Archive = "ARCHIVE",
    UnArchive = "UNARCHIVE",
    Pause = "PAUSE",
}

export type ActionName = string;

export type ProcessVersionId = number;

export type BuildInfoType = {
    buildTime: string;
    gitCommit: string;
    name: string;
    version: string;
};

export type ProcessActionType = {
    performedAt: Instant;
    user: string;
    actionName: ActionName;
    commentId?: number;
    comment?: string;
    buildInfo?: BuildInfoType;
    processVersionId: ProcessVersionId;
};

export type ProcessVersionType = {
    createDate: string;
    user: string;
    actions: Array<ProcessActionType>;
    modelVersion: number;
    processVersionId: ProcessVersionId;
};

export interface Scenario {
    name: string;
    processVersionId: number;
    isArchived: boolean;
    isFragment: boolean;
    isLatestVersion: boolean;
    processCategory: string;
    modificationDate: Instant; // Deprecated
    modifiedBy: string;
    createdAt: Instant;
    modifiedAt: Instant;
    createdBy: string;
    labels: string[];
    lastAction?: ProcessActionType;
    lastDeployedAction?: ProcessActionType;
    state: ProcessStateType;
    history?: ProcessVersionType[];
    scenarioGraph: ScenarioGraph;
    validationResult: ValidationResult;
    processingType: string;
    processingMode: ProcessingMode;
    engineSetupName: string;
}

export type ProcessName = Scenario["name"];

export type ProcessStateType = {
    status: StatusType;
    externalDeploymentId?: string;
    allowedActions: Array<ActionName>;
    icon: string;
    tooltip: string;
    description: string;
    startTime?: Date;
    attributes?: UnknownRecord;
    errors?: Array<string>;
    version?: number | null;
};

export type StatusType = {
    name: string;
};

export type StatusDefinitionType = {
    name: string;
    displayableName: string;
    icon: string;
    tooltip: string;
    categories: Array<string>;
};
