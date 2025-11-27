export type ActivityType =
    | "SCENARIO_CREATED"
    | "SCENARIO_ARCHIVED"
    | "SCENARIO_UNARCHIVED"
    | "SCENARIO_DEPLOYED"
    | "SCENARIO_REDEPLOYED"
    | "SCENARIO_CANCELED"
    | "SCENARIO_MODIFIED"
    | "SCENARIO_MODIFIED_WITH_AUTOSAVE"
    | "SCENARIO_PAUSED"
    | "SCENARIO_NAME_CHANGED"
    | "COMMENT_ADDED"
    | "ATTACHMENT_ADDED"
    | "CHANGED_PROCESSING_MODE"
    | "INCOMING_MIGRATION"
    | "OUTGOING_MIGRATION"
    | "PERFORMED_SINGLE_EXECUTION"
    | "PERFORMED_SCHEDULED_EXECUTION"
    | "AUTOMATIC_UPDATE";

export enum ActivityTypesRelatedToExecutions {
    ScenarioDeployed = "SCENARIO_DEPLOYED",
    ScenarioRedeployed = "SCENARIO_REDEPLOYED",
    ScenarioCanceled = "SCENARIO_CANCELED",
    PerformedSingleExecution = "PERFORMED_SINGLE_EXECUTION",
    PerformedScheduledExecution = "PERFORMED_SCHEDULED_EXECUTION",
}

export interface ActivityMetadata {
    type: ActivityType;
    displayableName: string;
    icon: string;
    supportedActions: string[];
}

export interface ActionMetadata {
    id: "compare" | "delete_comment" | "add_comment" | "edit_comment" | "download_attachment" | "delete_attachment";
    displayableName: string;
    icon: string;
}

export type ActivityAdditionalFields = { name: string; value: string; isDate?: boolean };

export type ActivityCommentContentStatus = "AVAILABLE" | "NOT_AVAILABLE";

export interface ActivityComment {
    content: {
        value?: string;
        status: ActivityCommentContentStatus;
    };
    lastModifiedBy: string;
    lastModifiedAt: string;
}

interface ActivityAttachmentDeleteStatus {
    status: "DELETED";
}

interface ActivityAttachmentAvailableStatus {
    id: number;
    status: "AVAILABLE";
}

export interface ActivityAttachment {
    file: ActivityAttachmentDeleteStatus | ActivityAttachmentAvailableStatus;
    filename: string;
    lastModifiedBy: string;
    lastModifiedAt: string;
}

export interface ActivitiesResponse {
    activities: {
        id: string;
        type: ActivityType;
        user: string;
        date: string;
        scenarioVersionId: number;
        comment?: ActivityComment | null;
        attachment?: ActivityAttachment;
        overrideDisplayableName?: string | null;
        overrideSupportedActions?: string[] | null;
        overrideIcon?: string | null;
        additionalFields: ActivityAdditionalFields[];
    }[];
}

export interface ActivityMetadataResponse {
    activities: ActivityMetadata[];
    actions: ActionMetadata[];
}

export type ModifyActivityCommentMeta = {
    existingComment?: string;
    scenarioActivityId: string;
    placeholder?: string;
    confirmButtonText: string;
};
