/* eslint-disable i18next/no-literal-string */
import type { ProcessingMode } from "../../http/HttpService";
import type { ScenarioGraph, ValidationResult } from "../../types";
import type { Instant } from "../../types/common";

export enum PredefinedActionName {
    Deploy = "DEPLOY",
    Redeploy = "REDEPLOY",
    Cancel = "CANCEL",
    Archive = "ARCHIVE",
    UnArchive = "UNARCHIVE",
    Pause = "PAUSE",
    RunOffSchedule = "RUN_OFF_SCHEDULE",
}

export type ActionName = string;

export type ProcessVersionId = number;

export type ProcessVersionType = {
    createDate: string;
    user: string;
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
    visibleActions: Array<ActionName>;
    allowedActions: Array<ActionName>;
    actionTooltips: Record<ActionName, string>;
    icon: string;
    tooltip: string;
    description: string;
};

export const KnownStatusName = {
    Running: "RUNNING",
    Scheduled: "SCHEDULED",
    Deploying: "DURING_DEPLOY",
    Redeploying: "DURING_REDEPLOY",
};

export type StatusType = StatusRunning | { name: string };

export type StatusRunning = {
    name: typeof KnownStatusName.Running;
    startedAt: string;
    versionId: string;
};

export function isStatusRunning(statusType?: StatusType): statusType is StatusRunning {
    return statusType?.name === KnownStatusName.Running;
}

export type StatusDefinitionType = {
    name: string;
    displayableName: string;
    icon: string;
    tooltip: string;
    categories: Array<string>;
};
