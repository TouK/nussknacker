import { ActivitiesResponse } from "../../src/http/HttpService";

export const sampleActivitiesResponse: ActivitiesResponse["activities"] = [
    {
        id: "48f383f9-ccdd-46b6-9b33-5f6693165755",
        user: "admin",
        date: "2022-01-22T06:09:44.313094Z",
        scenarioVersionId: 1,
        comment: null,
        attachment: {
            file: {
                id: 1,
                status: "AVAILABLE",
            },
            filename: "324.log",
            lastModifiedBy: "admin",
            lastModifiedAt: "2022-01-22T06:09:44.313094Z",
        },
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: null,
        overrideSupportedActions: null,
        type: "ATTACHMENT_ADDED",
    },
    {
        id: "a2576467-9bf9-4a92-b71f-be95b84d59f6",
        user: "admin",
        date: "2024-09-22T09:53:40.875721Z",
        scenarioVersionId: 3,
        comment: {
            content: {
                value: "tests save",
                status: "AVAILABLE",
            },
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-22T09:53:40.875721Z",
        },
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: "Version 3 saved",
        overrideSupportedActions: null,
        type: "SCENARIO_MODIFIED",
    },
    {
        id: "15c0e8a9-d1c5-47dd-bf28-8a08217fff5b",
        user: "admin",
        date: "2024-09-25T09:55:04.309Z",
        scenarioVersionId: 4,
        comment: {
            content: {
                value: "122",
                status: "AVAILABLE",
            },
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-25T09:55:04.309Z",
        },
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: "Version 4 saved",
        overrideSupportedActions: null,
        type: "SCENARIO_MODIFIED",
    },
    {
        id: "ec245dc5-f84c-4880-8727-b8c999c35a3f",
        user: "admin",
        date: "2024-09-26T10:26:28.293423Z",
        scenarioVersionId: 6,
        comment: null,
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: null,
        overrideSupportedActions: null,
        type: "SCENARIO_ARCHIVED",
    },
    {
        id: "1ba9d6c6-c61a-42f5-8311-4d0f287867f7",
        user: "admin",
        date: "2024-09-26T10:26:42.999982Z",
        scenarioVersionId: 6,
        comment: null,
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: null,
        overrideSupportedActions: null,
        type: "SCENARIO_UNARCHIVED",
    },
];

export const sampleActivitiesWithRepetiveResponse: ActivitiesResponse["activities"] = [
    {
        id: "56a7dd49-778b-468b-8e33-99bd176218aa",
        user: "admin",
        date: "2024-09-25T06:09:03.470213Z",
        scenarioVersionId: 1,
        comment: {
            content: {
                value: "test",
                status: "AVAILABLE",
            },
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-25T06:09:03.470213Z",
        },
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: null,
        overrideSupportedActions: null,
        type: "COMMENT_ADDED",
    },
    {
        id: "48f383f9-ccdd-46b6-9b33-5f6693165755",
        user: "admin",
        date: "2024-09-25T06:09:44.313094Z",
        scenarioVersionId: 1,
        comment: null,
        attachment: {
            file: {
                id: 1,
                status: "AVAILABLE",
            },
            filename: "324.log",
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-25T06:09:44.313094Z",
        },
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: null,
        overrideSupportedActions: null,
        type: "ATTACHMENT_ADDED",
    },
    {
        id: "a2576467-9bf9-4a92-b71f-be95b84d59f6",
        user: "admin",
        date: "2024-09-25T09:53:40.875721Z",
        scenarioVersionId: 3,
        comment: {
            content: {
                value: "tests save",
                status: "AVAILABLE",
            },
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-25T09:53:40.875721Z",
        },
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: "Version 3 saved",
        overrideSupportedActions: null,
        type: "SCENARIO_MODIFIED",
    },
    {
        id: "15c0e8a9-d1c5-47dd-bf28-8a08217fff5b",
        user: "admin",
        date: "2024-09-25T09:55:04.309Z",
        scenarioVersionId: 4,
        comment: {
            content: {
                value: "122",
                status: "AVAILABLE",
            },
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-25T09:55:04.309Z",
        },
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: "Version 4 saved",
        overrideSupportedActions: null,
        type: "SCENARIO_MODIFIED",
    },
    {
        id: "0182764d-c568-403e-88ea-24942a091af5",
        user: "admin",
        date: "2024-09-26T07:17:19.892192Z",
        scenarioVersionId: 5,
        comment: {
            content: {
                value: "test",
                status: "AVAILABLE",
            },
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-26T07:17:19.892192Z",
        },
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: "Version 5 saved",
        overrideSupportedActions: null,
        type: "SCENARIO_MODIFIED",
    },
    {
        id: "2cf1f252-3be1-413f-ad62-ccb311683744",
        user: "admin",
        date: "2024-09-26T10:08:00.895385Z",
        scenarioVersionId: 6,
        comment: {
            content: {
                value: "test12345",
                status: "AVAILABLE",
            },
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-26T10:08:00.895385Z",
        },
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: "Version 6 saved",
        overrideSupportedActions: null,
        type: "SCENARIO_MODIFIED",
    },
    {
        id: "c0745a74-937c-4682-b9c8-cb94f619eb14",
        user: "admin",
        date: "2024-09-26T10:11:29.657265Z",
        scenarioVersionId: 6,
        comment: {
            content: {
                value: "Scenario migrated from local by admin",
                status: "AVAILABLE",
            },
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-26T10:11:29.657265Z",
        },
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: "Version 6 saved",
        overrideSupportedActions: null,
        type: "SCENARIO_MODIFIED",
    },
    {
        id: "35fa34fe-7f07-4b50-98fb-58d19d5b7704",
        user: "admin",
        date: "2024-09-26T10:13:11.571064Z",
        scenarioVersionId: 6,
        comment: {
            content: {
                value: "Scenario migrated from local by admin",
                status: "AVAILABLE",
            },
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-26T10:13:11.571064Z",
        },
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: "Version 6 saved",
        overrideSupportedActions: null,
        type: "SCENARIO_MODIFIED",
    },
    {
        id: "4e8c9f40-7c2c-434e-8528-68dccf66d5d1",
        user: "admin",
        date: "2024-09-26T10:22:45.494475Z",
        scenarioVersionId: 6,
        comment: {
            content: {
                value: "Scenario migrated from local by admin",
                status: "AVAILABLE",
            },
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-26T10:22:45.494475Z",
        },
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: "Version 6 saved",
        overrideSupportedActions: null,
        type: "SCENARIO_MODIFIED",
    },
    {
        id: "ec245dc5-f84c-4880-8727-b8c999c35a3f",
        user: "admin",
        date: "2024-09-26T10:26:28.293423Z",
        scenarioVersionId: 6,
        comment: null,
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: null,
        overrideSupportedActions: null,
        type: "SCENARIO_ARCHIVED",
    },
    {
        id: "1ba9d6c6-c61a-42f5-8311-4d0f287867f7",
        user: "admin",
        date: "2024-09-26T10:26:42.999982Z",
        scenarioVersionId: 6,
        comment: null,
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: null,
        overrideSupportedActions: null,
        type: "SCENARIO_UNARCHIVED",
    },
];
