import { useActivitiesSearch } from "./useActivitiesSearch";
import { act, renderHook } from "@testing-library/react";
import { extendActivitiesWithUIData } from "./helpers/extendActivitiesWithUIData";
import { ActivitiesResponse, ActivityMetadataResponse } from "../../../http/HttpService";
import { mergeActivityDataWithMetadata } from "./helpers/mergeActivityDataWithMetadata";

const sampleMetadataResponse: ActivityMetadataResponse = {
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

const sampleActivitiesResponse: ActivitiesResponse["activities"] = [
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
        date: "2024-09-27T09:55:04.309Z",
        scenarioVersionId: 4,
        comment: {
            content: {
                value: "122",
                status: "AVAILABLE",
            },
            lastModifiedBy: "admin",
            lastModifiedAt: "2024-09-27T09:55:04.309Z",
        },
        attachment: null,
        additionalFields: [],
        overrideIcon: null,
        overrideDisplayableName: "Version 4 saved",
        overrideSupportedActions: null,
        type: "SCENARIO_MODIFIED",
    },
];

const mockedActivities = extendActivitiesWithUIData(mergeActivityDataWithMetadata(sampleActivitiesResponse, sampleMetadataResponse));

describe(useActivitiesSearch.name, () => {
    it.each<[string, string[]]>([
        ["atta", [mockedActivities[3].uiGeneratedId]],
        ["3 saved", [mockedActivities[2].uiGeneratedId]],
        ["2024-09-27", [mockedActivities[0].uiGeneratedId]],
        ["tests save", [mockedActivities[2].uiGeneratedId]],
    ])("should find elements when query is '%s'", (searchQuery, expected) => {
        const handleScrollToItemMock = jest.fn();
        const { result } = renderHook(() =>
            useActivitiesSearch({
                activities: mockedActivities,
                handleScrollToItem: handleScrollToItemMock,
            }),
        );

        act(() => {
            result.current.handleSearch(searchQuery);
        });

        expect(result.current.foundResults).toEqual(expected);
    });
});
