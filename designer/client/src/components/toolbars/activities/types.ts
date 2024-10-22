export type ActivityTypes =
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

export type ActivityAdditionalFields = { name: string; value: string };

export interface ActivityComment {
    content: {
        value: string;
        status: "AVAILABLE" | "DELETED";
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
        type: ActivityTypes;
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
