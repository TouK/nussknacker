import { ActivityMetadataResponse } from "../../src/http/HttpService";

export const sampleMetadataResponse: ActivityMetadataResponse = {
    activities: [
        {
            type: "SCENARIO_CREATED",
            displayableName: "Scenario created",
            icon: "/assets/states/success.svg",
            supportedActions: [],
        },
        {
            type: "SCENARIO_ARCHIVED",
            displayableName: "Scenario archived",
            icon: "/assets/process/archived.svg",
            supportedActions: [],
        },
        {
            type: "SCENARIO_UNARCHIVED",
            displayableName: "Scenario unarchived",
            icon: "/assets/process/success.svg",
            supportedActions: [],
        },
        {
            type: "SCENARIO_DEPLOYED",
            displayableName: "Deployment",
            icon: "/assets/states/deploy.svg",
            supportedActions: ["delete_comment", "edit_comment"],
        },
        {
            type: "SCENARIO_PAUSED",
            displayableName: "Pause",
            icon: "/assets/states/stopping.svg",
            supportedActions: ["delete_comment", "edit_comment"],
        },
        {
            type: "SCENARIO_CANCELED",
            displayableName: "Cancel",
            icon: "/assets/states/stopping.svg",
            supportedActions: ["delete_comment", "edit_comment"],
        },
        {
            type: "SCENARIO_MODIFIED",
            displayableName: "New version saved",
            icon: "/assets/states/success.svg",
            supportedActions: ["delete_comment", "edit_comment", "compare"],
        },
        {
            type: "SCENARIO_NAME_CHANGED",
            displayableName: "Scenario name changed",
            icon: "/assets/states/success.svg",
            supportedActions: [],
        },
        {
            type: "COMMENT_ADDED",
            displayableName: "Comment",
            icon: "/assets/states/success.svg",
            supportedActions: ["delete_comment", "edit_comment"],
        },
        {
            type: "ATTACHMENT_ADDED",
            displayableName: "Attachment",
            icon: "/assets/states/success.svg",
            supportedActions: ["download_attachment", "delete_attachment"],
        },
        {
            type: "CHANGED_PROCESSING_MODE",
            displayableName: "Processing mode change",
            icon: "/assets/states/success.svg",
            supportedActions: [],
        },
        {
            type: "INCOMING_MIGRATION",
            displayableName: "Incoming migration",
            icon: "/assets/states/success.svg",
            supportedActions: ["compare"],
        },
        {
            type: "OUTGOING_MIGRATION",
            displayableName: "Outgoing migration",
            icon: "/assets/states/success.svg",
            supportedActions: ["delete_comment", "edit_comment"],
        },
        {
            type: "PERFORMED_SINGLE_EXECUTION",
            displayableName: "Processing data",
            icon: "/assets/states/success.svg",
            supportedActions: ["delete_comment", "edit_comment"],
        },
        {
            type: "PERFORMED_SCHEDULED_EXECUTION",
            displayableName: "Processing data",
            icon: "/assets/states/success.svg",
            supportedActions: [],
        },
        {
            type: "AUTOMATIC_UPDATE",
            displayableName: "Automatic update",
            icon: "/assets/states/success.svg",
            supportedActions: ["compare"],
        },
        {
            type: "CUSTOM_ACTION",
            displayableName: "Custom action",
            icon: "/assets/states/success.svg",
            supportedActions: [],
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
