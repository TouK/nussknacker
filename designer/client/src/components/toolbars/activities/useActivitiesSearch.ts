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

            if (activity.activities.displayableName.toLowerCase().includes(value.toLowerCase())) {
                setFoundResults((prevState) => {
                    prevState.push(activity.id);
                    return prevState;
                });
            }
        }
    };

    const changeResult = (selectedResultNewValue: number) => {
        const foundResult = foundResults[selectedResultNewValue];
        listRef.current.scrollToItem(
            activities.findIndex((item) => item.id === foundResult),
            "center",
        );
        setSelectedResult(selectedResultNewValue);
    };

    return { handleSearch, foundResults, selectedResult, searchQuery, changeResult };
};
