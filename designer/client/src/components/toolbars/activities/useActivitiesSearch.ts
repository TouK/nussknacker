import { useState } from "react";
import { Activity, UIActivity } from "./ActivitiesPanel";
import { Align } from "react-window";
import { NestedKeyOf } from "../../../reducers/graph/nestedKeyOf";
import { get } from "lodash";

interface Props {
    activities: UIActivity[];
    handleScrollToItem: (index: number, align: Align) => void;
}
export const useActivitiesSearch = ({ activities, handleScrollToItem }: Props) => {
    const [searchQuery, setSearchQuery] = useState<string>("");
    const [foundResults, setFoundResults] = useState<string[]>([]);
    const [selectedResult, setSelectedResult] = useState<number>(0);

    const handleSearch = (value: string) => {
        setSearchQuery(value);
        setFoundResults([]);

        const fullSearchFields: NestedKeyOf<Activity>[] = [
            "date",
            "user",
            "comment.content.value",
            "activities.displayableName",
            "overrideDisplayableName",
        ];

        for (const activity of activities) {
            if (activity.uiType !== "item") {
                continue;
            }

            for (const fullSearchField of fullSearchFields) {
                const searchFieldValue = get(activity, fullSearchField, "") || "";
                if (value && searchFieldValue.toLowerCase().includes(value.toLowerCase())) {
                    setFoundResults((prevState) => {
                        if (prevState.every((foundResult) => foundResult != activity.uiGeneratedId)) {
                            prevState.push(activity.uiGeneratedId);
                        }
                        return prevState;
                    });
                }
            }
        }

        handleScrollToItem(
            activities.findIndex((item) => item.uiGeneratedId === foundResults[selectedResult]),
            "start",
        );
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
