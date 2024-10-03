import React, { CSSProperties, memo, useEffect, useMemo, useRef } from "react";
import { DateItem, ActivityItem, ButtonItem } from "./ActivityPanelRowItem";
import { UIActivity } from "./ActivitiesPanel";

interface Props {
    index: number;
    style?: CSSProperties | undefined;
    setRowHeight: (index: number, height: number) => void;
    handleShowRow(index: number, sameItemOccurrence: number): void;
    handleHideRow(index: number, sameItemOccurrence: number): void;
    activities: UIActivity[];
    searchQuery: string;
}

export const ActivitiesPanelRow = memo(({ index, style, setRowHeight, handleShowRow, handleHideRow, activities, searchQuery }: Props) => {
    const rowRef = useRef<HTMLDivElement>(null);
    const activity = useMemo(() => activities[index], [activities, index]);
    const firstDeployedIndex = useMemo(
        () => activities.findIndex((activeItem) => activeItem.uiType === "item" && activeItem.type === "SCENARIO_DEPLOYED"),
        [activities],
    );
    const isActiveDeployedItem = firstDeployedIndex === index;

    useEffect(() => {
        if (rowRef.current) {
            setRowHeight(index, rowRef.current.clientHeight);
        }
    }, [index, rowRef, setRowHeight]);

    const itemToRender = useMemo(() => {
        switch (activity.uiType) {
            case "item": {
                return <ActivityItem activity={activity} ref={rowRef} isActiveItem={isActiveDeployedItem} searchQuery={searchQuery} />;
            }
            case "date": {
                return <DateItem activity={activity} ref={rowRef} />;
            }
            case "toggleItemsButton": {
                return (
                    <div ref={rowRef}>
                        {activity.isClicked ? (
                            <ButtonItem handleHideRow={() => handleHideRow(index, activity.sameItemOccurrence)}>Show less</ButtonItem>
                        ) : (
                            <ButtonItem handleHideRow={() => handleShowRow(index, activity.sameItemOccurrence)}>
                                Show {activity.sameItemOccurrence} more
                            </ButtonItem>
                        )}
                    </div>
                );
            }
            default: {
                return null;
            }
        }
    }, [activity, handleHideRow, handleShowRow, index, isActiveDeployedItem, searchQuery]);

    return (
        <div key={activity.uiGeneratedId} style={style}>
            {itemToRender}
        </div>
    );
});

ActivitiesPanelRow.displayName = "ActivitiesPanelRow";
