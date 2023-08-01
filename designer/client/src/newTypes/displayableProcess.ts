import { Opaque } from "type-fest";
import { BuildInfoType } from "../components/Process/types";
import { EdgeTypeTaggedUnion, NodeDataTaggedUnion, UIParameter, URI } from "./processDefinitionData";
import { TypingResult } from "./typingResult";

export type TypedProcessType = BaseProcessDetails<ValidatedDisplayableProcess>;

type ApiProcessId = Opaque<number, "ApiProcessId">;
type VersionId = Opaque<number, "VersionId">;
type ProcessingType = Opaque<string, "ProcessingType">;
type Instant = Opaque<string, "Instant">;
type ProcessActionId = Opaque<number, "ProcessActionId">;

enum ProcessActionType {
    Deploy = "DEPLOY",
    Cancel = "CANCEL",
    Archive = "ARCHIVE",
    UnArchive = "UNARCHIVE",
    Pause = "PAUSE",
    Rename = "RENAME",
}

enum ProcessActionState {
    InProgress = "IN_PROGRESS",
    Finished = "FINISHED",
    Failed = "FAILED",
}

export type ProcessAction = {
    id: ProcessActionId;
    processId: ApiProcessId;
    processVersionId: VersionId;
    user: string;
    createdAt: Instant;
    performedAt: Instant;
    actionType: ProcessActionType;
    state: ProcessActionState;
    failureMessage?: string;
    commentId?: number;
    comment?: string;
    buildInfo: BuildInfoType;
};

export type ProcessVersion = {
    processVersionId: VersionId;
    createDate: Instant;
    user: string;
    modelVersion?: number;
    actions: ProcessAction[];
};

type ExternalDeploymentId = Opaque<string, "ExternalDeploymentId">;
type Json = Record<string, unknown>;

type StateStatus = {
    name: string;
};

type ProcessState = {
    externalDeploymentId?: ExternalDeploymentId;
    status: StateStatus;
    version?: ProcessVersion;
    allowedActions: ProcessActionType[];
    icon: URI;
    tooltip: string;
    description: string;
    startTime?: number;
    attributes?: Json;
    errors: string[];
};

type BaseProcessDetails<ProcessShape extends DisplayableProcess> = {
    id: string;
    name: string;
    processId: ApiProcessId;
    processVersionId: VersionId;
    isLatestVersion?: boolean;
    description?: string;
    isArchived?: boolean;
    isFragment?: boolean;
    processingType: ProcessingType;
    processCategory: Opaque<string, "processCategory">;
    modificationDate: Instant;
    modifiedAt: Instant;
    modifiedBy: string;
    createdAt: Instant;
    createdBy: string;
    tags: string[];
    lastDeployedAction?: ProcessAction;
    lastStateAction?: ProcessAction;
    lastAction?: ProcessAction;
    json: ProcessShape;
    history: ProcessVersion[];
    modelVersion?: number;
    state?: ProcessState;
};

type ProcessAdditionalFields = {
    description?: string;
    properties: Record<string, string>;
    metaDataType: string;
};

export type ProcessProperties = {
    additionalFields: ProcessAdditionalFields;
    isFragment?: boolean;
};

export type Edge = {
    from: NodeData["id"];
    to: NodeData["id"];
    edgeType?: EdgeTypeTaggedUnion;
};

type NodeData = NodeDataTaggedUnion;

type DisplayableProcess = {
    id: string;
    properties: ProcessProperties;
    nodes: NodeData[];
    edges: Edge[];
    processingType: ProcessingType;
    category: string;
};

enum NodeValidationErrorType {
    RenderNotAllowed = "RenderNotAllowed",
    SaveNotAllowed = "SaveNotAllowed",
    SaveAllowed = "SaveAllowed",
}

type NodeValidationError = {
    typ: string;
    message: string;
    description: string;
    fieldName?: string;
    errorType: NodeValidationErrorType;
};

type ValidationErrors = {
    invalidNodes: Record<string, NodeValidationError[]>;
    processPropertiesErrors: NodeValidationError[];
    globalErrors: NodeValidationError[];
};

type ValidationWarnings = {
    invalidNodes: Record<string, NodeValidationError[]>;
};

type NodeTypingData = {
    variableTypes: Record<string, TypingResult>;
    parameters?: UIParameter[];
    typingInfo: Record<string, TypingResult>;
};

type ValidationResult = {
    errors: ValidationErrors;
    warnings: ValidationWarnings;
    nodeResults: Record<string, NodeTypingData>;
};

export type ValidatedDisplayableProcess = DisplayableProcess & {
    validationResult?: ValidationResult;
};
