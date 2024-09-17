import { MutableRefObject, useState } from "react";
import { Activity } from "./ActivitiesPanel";
import { VariableSizeList } from "react-window";

interface Props {
    activities: Activity[];
    listRef: MutableRefObject<VariableSizeList>;
}
export const UseActivitiesSearch = ({ activities, listRef }: Props) => {
    const [searchQuery, setSearchQuery] = useState<string>("");
    const [foundResults, setFoundResults] = useState<string[]>([]);
    const [selectedResult, setSelectedResult] = useState<number>(0);

    const handleSearch = (value: string) => {
        setSearchQuery(value);
        setFoundResults([]);

        for (const activity of activities) {
            if (activity.ui.type !== "item") {
                continue;
            }

            if (value && activity.activities.displayableName.toLowerCase().includes(value.toLowerCase())) {
                setFoundResults((prevState) => {
                    prevState.push(activity.id);
                    return prevState;
                });
            }
        }

        listRef.current.scrollToItem(
            activities.findIndex((item) => item.id === foundResults[selectedResult]),
            "center",
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
            activities.findIndex((item) => item.id === foundResult),
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
