import React, { CSSProperties, memo, useEffect, useMemo, useRef } from "react";
import { Box, Divider, Typography } from "@mui/material";
import { MoreItemsButton } from "./MoreItemsButton";
import { LessItemsButton } from "./LessItemsButton";
import { ActivityItem } from "./ActivityItem";
import { UIActivities } from "./ActivitiesPanel";

interface Props {
    index: number;
    style?: CSSProperties | undefined;
    setRowHeight: (index: number, height: number) => void;
    handleShowRow(index: number, sameItemOccurrence: number): void;
    handleHideRow(index: number, sameItemOccurrence: number): void;
    activities: UIActivities[];
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
                return (
                    <Box display={"flex"} justifyContent={"center"} alignItems={"center"} px={1}>
                        <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.common.white, mr: 1 })} />
                        <Typography component={"div"} variant={"caption"} ref={rowRef}>
                            {activity.value}
                        </Typography>
                        <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.common.white, ml: 1 })} />
                    </Box>
                );
            }
            case "moreItemsButton": {
                return (
                    <div ref={rowRef}>
                        {activity.isClicked ? (
                            <LessItemsButton sameItemOccurrence={activity.sameItemOccurrence} handleHideRow={handleHideRow} index={index} />
                        ) : (
                            <MoreItemsButton sameItemOccurrence={activity.sameItemOccurrence} handleShowRow={handleShowRow} index={index} />
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
