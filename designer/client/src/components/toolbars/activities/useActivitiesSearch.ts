import { useCallback, useState } from "react";
import { Activity, UIActivity } from "./ActivitiesPanel";
import { Align } from "react-window";
import { NestedKeyOf } from "../../../reducers/graph/nestedKeyOf";
import { get, uniq } from "lodash";
import { ActivityAdditionalFields } from "../../../http/HttpService";

interface Props {
    activities: UIActivity[];
    handleScrollToItem: (index: number, align: Align) => void;
}
export const useActivitiesSearch = ({ activities, handleScrollToItem }: Props) => {
    const [searchQuery, setSearchQuery] = useState<string>("");
    const [foundResults, setFoundResults] = useState<string[]>([]);
    const [selectedResult, setSelectedResult] = useState<number>(0);

    const handleSetFoundResults = useCallback((activities: UIActivity[]) => {
        setFoundResults(uniq(activities).map((activity) => activity.uiGeneratedId));
    }, []);

    const handleSearch = (value: string) => {
        setSearchQuery(value);
        setFoundResults([]);

        const foundActivities: UIActivity[] = [];

        const fullSearchFields: NestedKeyOf<Activity>[] = [
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

            for (const fullSearchField of fullSearchFields) {
                const searchFieldValue: string | ActivityAdditionalFields[] = get(activity, fullSearchField, "") || "";

                if (Array.isArray(searchFieldValue)) {
                    if (
                        searchFieldValue.some((searchValue) =>
                            `${searchValue.name.toLowerCase()} ${searchValue.value.toLowerCase()}`.includes(value.toLowerCase()),
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

        handleSetFoundResults(foundActivities);

        const indexToScroll = activities.findIndex((item) => item.uiGeneratedId === foundActivities[selectedResult]?.uiGeneratedId);
        handleScrollToItem(indexToScroll, "center");
    };

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
    };

    const handleClearResults = () => {
        handleSearch("");
        setSelectedResult(0);
    };

    return { handleSearch, foundResults, selectedResult, searchQuery, changeResult, handleClearResults };
};
