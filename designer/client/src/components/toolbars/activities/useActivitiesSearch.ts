import { useCallback, useState } from "react";
import { Activity, UIActivity } from "./ActivitiesPanel";
import { Align } from "react-window";
import { NestedKeyOf } from "../../../reducers/graph/nestedKeyOf";
import { get, uniq } from "lodash";
import { ActivityAdditionalFields } from "./types";
import { handleToggleActivities } from "./helpers/handleToggleActivities";

interface Props {
    activities: UIActivity[];
    handleScrollToItem: (index: number, align: Align) => void;
    handleUpdateScenarioActivities: (activities: (activities: UIActivity[]) => UIActivity[]) => void;
}
export const useActivitiesSearch = ({ activities, handleScrollToItem, handleUpdateScenarioActivities }: Props) => {
    const [searchQuery, setSearchQuery] = useState<string>("");
    const [foundResults, setFoundResults] = useState<string[]>([]);
    const [selectedResult, setSelectedResult] = useState<number>(0);

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
            ];

            for (const activity of activities) {
                if (activity.uiType !== "item") {
                    continue;
                }

                for (const fullSearchAllowedField of fullSearchAllowedFields) {
                    const searchFieldValue: string | ActivityAdditionalFields[] = get(activity, fullSearchAllowedField, "") || "";

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

                    if (value && searchFieldValue.toLowerCase().includes(value.toLowerCase())) {
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
