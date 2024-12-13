export type ActivityType =
    | "SCENARIO_CREATED"
    | "SCENARIO_ARCHIVED"
    | "SCENARIO_UNARCHIVED"
    | "SCENARIO_DEPLOYED"
    | "SCENARIO_CANCELED"
    | "SCENARIO_MODIFIED"
    | "SCENARIO_PAUSED"
    | "SCENARIO_NAME_CHANGED"
    | "COMMENT_ADDED"
    | "ATTACHMENT_ADDED"
    | "CHANGED_PROCESSING_MODE"
    | "INCOMING_MIGRATION"
    | "OUTGOING_MIGRATION"
    | "PERFORMED_SINGLE_EXECUTION"
    | "PERFORMED_SCHEDULED_EXECUTION"
    | "AUTOMATIC_UPDATE"
    | "CUSTOM_ACTION";

export enum PredefinedActivityType {
    ScenarioDeployed = "SCENARIO_DEPLOYED",
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

export type ActivityAdditionalFields = { name: string; value: string };

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
        comment?: ActivityComment;
        attachment?: ActivityAttachment;
        overrideDisplayableName?: string;
        overrideSupportedActions?: string[];
        overrideIcon?: string;
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
