import { produce } from "immer";
import { get, uniq } from "lodash";
import { useCallback, useLayoutEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import type { Align } from "react-window";
import { useFreshRef } from "rooks";

import { updateSearchQuery } from "../../../actions/nk/scenarioActivities";
import type { NestedKeyOf } from "../../../reducers/graph/lodashWrappers";
import { getRunningVersion } from "../../../reducers/selectors/graph";
import { getSearchQuery } from "../../../reducers/selectors/processActivities";
import type { Activity, UIActivity } from "./ActivitiesPanel";
import { handleToggleActivities } from "./helpers/handleToggleActivities";
import type { ActivityAdditionalFields } from "./types";

export const predefinedQueries = {
    runningVersionQuery: "scenarioVersion:running version",
};

interface Props {
    activities: UIActivity[];
    handleScrollToItem: (index: number, align: Align) => void;
    handleUpdateScenarioActivities: (activities: (activities: UIActivity[]) => UIActivity[]) => void;
}

export const useActivitiesSearch = ({ activities, handleScrollToItem, handleUpdateScenarioActivities }: Props) => {
    const [foundResults, setFoundResults] = useState<string[]>([]);
    const [selectedResult, setSelectedResult] = useState<number>(0);

    const searchQuery = useSelector(getSearchQuery);
    const runningVersion = useSelector(getRunningVersion);

    const dispatch = useDispatch();

    const handleSetFoundResults = useCallback((activities: UIActivity[]) => {
        const uniqueFoundResults = uniq(activities).map((activity) => activity.uiGeneratedId);
        setFoundResults(uniqueFoundResults);

        return uniqueFoundResults;
    }, []);

    const handleUpdateSearchResults = useCallback(
        (foundActivities: string[], selectedResult: number) => {
            handleUpdateScenarioActivities(
                produce((prevState) => {
                    prevState.forEach((activity) => {
                        if (activity.uiType === "item") {
                            activity.isFound = foundActivities.some((foundResult) => foundResult === activity.uiGeneratedId);
                            activity.isActiveFound = activity.uiGeneratedId === foundActivities[selectedResult];
                        }
                    });
                }),
            );
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
        dispatch(updateSearchQuery(""));
        setSelectedResult(0);
        setFoundResults([]);
        handleUpdateSearchResults([], 0);
        handleCollapseAllResults();
    }, [dispatch, handleCollapseAllResults, handleUpdateSearchResults]);

    const handleSearch = useCallback(
        (value: string) => {
            dispatch(updateSearchQuery(value));
        },
        [dispatch],
    );

    const handleGetResults = useCallback(
        (value: string) => {
            handleExpandAllResults();
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
                        value === predefinedQueries.runningVersionQuery &&
                        (activity.type === "SCENARIO_REDEPLOYED" || activity.type === "SCENARIO_DEPLOYED");
                    if (isRunningVersion) {
                        if (parseInt(runningVersion, 10) === searchFieldValue && foundActivities.length === 0) {
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
            handleExpandAllResults,
            handleScrollToItem,
            handleSetFoundResults,
            handleUpdateSearchResults,
            runningVersion,
            selectedResult,
        ],
    );

    // Avoid rerender depth limit exceeded error in useLayoutEffect
    const handleGetResultsRef = useFreshRef(handleGetResults);

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

    useLayoutEffect(() => {
        if (searchQuery) {
            handleGetResultsRef.current(searchQuery);
        }

        if (searchQuery === "") {
            handleClearResults();
            return;
        }
    }, [handleClearResults, handleGetResultsRef, searchQuery]);

    return { handleSearch, foundResults, selectedResult, searchQuery, changeResult, handleClearResults };
};
