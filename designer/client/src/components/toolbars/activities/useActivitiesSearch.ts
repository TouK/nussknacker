import { get, uniq } from "lodash";
import { useCallback, useState } from "react";
import { useSelector } from "react-redux";
import type { Align } from "react-window";

import type { NestedKeyOf } from "../../../reducers/graph/lodashWrappers";
import { getRunningVersion } from "../../../reducers/selectors/graph";
import type { Activity, UIActivity } from "./ActivitiesPanel";
import { handleToggleActivities } from "./helpers/handleToggleActivities";
import type { ActivityAdditionalFields } from "./types";

interface Props {
    activities: UIActivity[];
    handleScrollToItem: (index: number, align: Align) => void;
    handleUpdateScenarioActivities: (activities: (activities: UIActivity[]) => UIActivity[]) => void;
}
export const useActivitiesSearch = ({ activities, handleScrollToItem, handleUpdateScenarioActivities }: Props) => {
    const [searchQuery, setSearchQuery] = useState<string>("");
    const [foundResults, setFoundResults] = useState<string[]>([]);
    const [selectedResult, setSelectedResult] = useState<number>(0);
    const runningVersion = useSelector(getRunningVersion);

    const handleSetFoundResults = useCallback((activities: UIActivity[]) => {
        const uniqueFoundResults = uniq(activities).map((activity) => activity.uiGeneratedId);
        setFoundResults(uniqueFoundResults);

        return uniqueFoundResults;
    }, []);

    const handleUpdateSearchResults = useCallback(
        (foundActivities: string[], selectedResult: number) => {
            handleUpdateScenarioActivities((prevState) => {
                return prevState.map((activity) => {
                    if (activity.uiType !== "item") {
                        return activity;
                    }

                    activity.isFound = false;
                    activity.isActiveFound = false;

                    if (foundActivities.some((foundResult) => foundResult === activity.uiGeneratedId)) {
                        activity.isFound = true;
                    }

                    if (activity.uiGeneratedId === foundActivities[selectedResult]) {
                        activity.isActiveFound = true;
                    }

                    return activity;
                });
            });
        },
        [handleUpdateScenarioActivities],
    );

    const handleExpandAllResults = useCallback(() => {
        handleUpdateScenarioActivities((prevState) => {
            let newState = [...prevState];

            for (const activity of newState) {
                if (activity.uiType === "toggleItemsButton") {
                    newState = handleToggleActivities(newState, activity.uiGeneratedId, activity.sameItemOccurrence, "expand").uiActivities;
                }
            }

            return newState;
        });
    }, [handleUpdateScenarioActivities]);

    const handleCollapseAllResults = useCallback(() => {
        handleUpdateScenarioActivities((prevState) => {
            let newState = [...prevState];

            for (const activity of newState) {
                if (activity.uiType === "toggleItemsButton") {
                    newState = handleToggleActivities(
                        newState,
                        activity.uiGeneratedId,
                        activity.sameItemOccurrence,
                        "collapse",
                    ).uiActivities;
                }
            }

            return newState;
        });
    }, [handleUpdateScenarioActivities]);

    const handleClearResults = useCallback(() => {
        setSearchQuery("");
        setSelectedResult(0);
        setFoundResults([]);
        handleUpdateSearchResults([], 0);
        handleCollapseAllResults();
    }, [handleCollapseAllResults, handleUpdateSearchResults]);

    const handleSearch = useCallback(
        (value: string) => {
            handleExpandAllResults();
            setSearchQuery(value);

            if (value === "") {
                handleClearResults();
                return;
            }

            setSelectedResult(0);

            const foundActivities: UIActivity[] = [];

            const fullSearchAllowedFields: NestedKeyOf<Activity>[] = [
                "date",
                "user",
                "comment.content.value",
                "activities.displayableName",
                "overrideDisplayableName",
                "additionalFields",
                "scenarioVersionId",
            ];

            for (const activity of activities) {
                if (activity.uiType !== "item") {
                    continue;
                }

                for (const fullSearchAllowedField of fullSearchAllowedFields) {
                    const searchFieldValue: string | number | ActivityAdditionalFields[] = get(activity, fullSearchAllowedField, "") || "";

                    const isRunningVersion =
                        fullSearchAllowedField === "scenarioVersionId" &&
                        value === "scenarioVersion:running version" &&
                        (activity.type === "SCENARIO_REDEPLOYED" || activity.type === "SCENARIO_DEPLOYED");
                    if (isRunningVersion) {
                        if (parseInt(runningVersion, 10) === searchFieldValue && foundActivities.length === 0) {
                            console.log(activity);
                            foundActivities.push(activity);
                        }

                        continue;
                    }

                    if (Array.isArray(searchFieldValue)) {
                        if (
                            searchFieldValue.some((searchValue) =>
                                `${searchValue.name.toLowerCase()}: ${searchValue.value.toLowerCase()}`.includes(value.toLowerCase()),
                            )
                        ) {
                            foundActivities.push(activity);
                        }

                        continue;
                    }

                    if (value && typeof searchFieldValue === "string" && searchFieldValue.toLowerCase().includes(value.toLowerCase())) {
                        foundActivities.push(activity);
                    }
                }
            }

            const uniqueFoundResults = handleSetFoundResults(foundActivities);
            handleUpdateSearchResults(uniqueFoundResults, selectedResult);
            const indexToScroll = activities.findIndex((item) => item.uiGeneratedId === foundActivities[0]?.uiGeneratedId);
            handleScrollToItem(indexToScroll, "center");
        },
        [
            activities,
            handleClearResults,
            handleExpandAllResults,
            handleScrollToItem,
            handleSetFoundResults,
            handleUpdateSearchResults,
            runningVersion,
            selectedResult,
        ],
    );

    const changeResult = (selectedResultNewValue: number) => {
        if (selectedResultNewValue < 0) {
            selectedResultNewValue = foundResults.length - 1;
        }

        if (selectedResultNewValue >= foundResults.length) {
            selectedResultNewValue = 0;
        }

        const foundResult = foundResults[selectedResultNewValue];
        handleScrollToItem(
            activities.findIndex((item) => item.uiGeneratedId === foundResult),
            "center",
        );
        setSelectedResult(selectedResultNewValue);
        handleUpdateSearchResults(foundResults, selectedResultNewValue);
    };

    return { handleSearch, foundResults, selectedResult, searchQuery, changeResult, handleClearResults };
};
