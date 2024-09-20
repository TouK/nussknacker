import { MutableRefObject, useState } from "react";
import { Activity, UIActivities } from "./ActivitiesPanel";
import { VariableSizeList } from "react-window";
import { NestedKeyOf } from "../../../reducers/graph/nestedKeyOf";
import { get } from "lodash";

interface Props {
    activities: UIActivities[];
    listRef: MutableRefObject<VariableSizeList>;
}
export const UseActivitiesSearch = ({ activities, listRef }: Props) => {
    const [searchQuery, setSearchQuery] = useState<string>("");
    const [foundResults, setFoundResults] = useState<string[]>([]);
    const [selectedResult, setSelectedResult] = useState<number>(0);

    const handleSearch = (value: string) => {
        setSearchQuery(value);
        setFoundResults([]);

        const fullSearchFields: NestedKeyOf<Activity>[] = ["date", "user", "comment", "activities.displayableName"];

        for (const activity of activities) {
            if (activity.uiType !== "item") {
                continue;
            }

            for (const fullSearchField of fullSearchFields) {
                if (value && get(activity, fullSearchField, "").toLowerCase().includes(value.toLowerCase())) {
                    setFoundResults((prevState) => {
                        prevState.push(activity.uiGeneratedId);
                        return prevState;
                    });
                }
            }

            if (value && activity.date.toLowerCase().includes(value.toLowerCase())) {
                setFoundResults((prevState) => {
                    prevState.push(activity.uiGeneratedId);
                    return prevState;
                });
            }
        }

        listRef.current.scrollToItem(
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
        listRef.current.scrollToItem(
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
