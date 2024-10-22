import { useActivitiesSearch } from "./useActivitiesSearch";
import { act, renderHook } from "@testing-library/react";
import { extendActivitiesWithUIData } from "./helpers/extendActivitiesWithUIData";
import { ActivitiesResponse } from "./types";
import { mergeActivityDataWithMetadata } from "./helpers/mergeActivityDataWithMetadata";
import { sampleMetadataResponse } from "../../../../__mocks__/fixtures/sampleMetadataResponse";

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
        additionalFields: [],
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
        additionalFields: [],
        overrideDisplayableName: "Version 3 saved",
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
        additionalFields: [],
        overrideDisplayableName: "Version 4 saved",
        type: "SCENARIO_MODIFIED",
    },
    {
        id: "da3d1f78-7d73-4ed9-b0e5-95538e150d0d",
        user: "some user",
        date: "2022-12-17T14:21:17Z",
        scenarioVersionId: 1,
        additionalFields: [
            {
                name: "oldName",
                value: "marketing campaign",
            },
            {
                name: "newName",
                value: "old marketing campaign",
            },
        ],
        type: "SCENARIO_NAME_CHANGED",
    },
];

const mockedActivities = extendActivitiesWithUIData(mergeActivityDataWithMetadata(sampleActivitiesResponse, sampleMetadataResponse));

describe(useActivitiesSearch.name, () => {
    it.each<[string, string[]]>([
        ["atta", [mockedActivities[4].uiGeneratedId]],
        ["3 saved", [mockedActivities[3].uiGeneratedId]],
        ["2024-09-27", [mockedActivities[1].uiGeneratedId]],
        ["tests save", [mockedActivities[3].uiGeneratedId]],
        ["newName: old marketing campaign", [mockedActivities[7].uiGeneratedId]],
    ])("should find elements when query is '%s'", (searchQuery, expected) => {
        const handleScrollToItemMock = jest.fn();
        const handleUpdateScenarioActivitiesMock = jest.fn();

        const { result } = renderHook(() =>
            useActivitiesSearch({
                activities: mockedActivities,
                handleScrollToItem: handleScrollToItemMock,
                handleUpdateScenarioActivities: handleUpdateScenarioActivitiesMock,
            }),
        );

        act(() => {
            result.current.handleSearch(searchQuery);
        });

        expect(result.current.foundResults).toMatchObject(expected);
    });
});
