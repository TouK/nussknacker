import React, { CSSProperties, memo, useEffect, useMemo, useRef } from "react";
import { Box, Divider, Typography } from "@mui/material";
import { MoreItemsButton } from "./MoreItemsButton";
import { LessItemsButton } from "./LessItemsButton";
import { ActivityItem } from "./ActivityItem";
import { Activity } from "./ActivitiesPanel";

interface Props {
    index: number;
    style?: CSSProperties | undefined;
    setRowHeight: (index: number, height: number) => void;
    handleShowRow(index: number, sameItemOccurrence: number): void;
    handleHideRow(index: number, sameItemOccurrence: number): void;
    activities: Activity[];
    searchQuery: string;
}

export const ActivitiesPanelRow = memo(({ index, style, setRowHeight, handleShowRow, handleHideRow, activities, searchQuery }: Props) => {
    const rowRef = useRef<HTMLDivElement>(null);
    const activity = useMemo(() => activities[index], [activities, index]);
    const firstDeployedIndex = useMemo(() => activities.findIndex((activeItem) => activeItem.type === "SCENARIO_DEPLOYED"), [activities]);
    const isActiveDeployedItem = firstDeployedIndex === index;

    useEffect(() => {
        if (rowRef.current) {
            setRowHeight(index, rowRef.current.clientHeight);
        }
    }, [index, rowRef, setRowHeight]);

    const itemToRender = useMemo(() => {
        switch (activity.ui.type) {
            case "item": {
                return <ActivityItem activity={activity} ref={rowRef} isActiveItem={isActiveDeployedItem} searchQuery={searchQuery} />;
            }
            case "date": {
                return (
                    <Box display={"flex"} justifyContent={"center"} alignItems={"center"} px={1}>
                        <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.common.white, mr: 1 })} />
                        <Typography component={"div"} variant={"caption"} ref={rowRef}>
                            {activity.ui.value}
                        </Typography>
                        <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.common.white, ml: 1 })} />
                    </Box>
                );
            }
            case "moreItemsButton": {
                return (
                    <div ref={rowRef}>
                        {activity.ui.isClicked ? (
                            <MoreItemsButton
                                sameItemOccurrence={activity.ui.sameItemOccurrence}
                                handleShowRow={handleShowRow}
                                index={index}
                            />
                        ) : (
                            <LessItemsButton
                                sameItemOccurrence={activity.ui.sameItemOccurrence}
                                handleHideRow={handleHideRow}
                                index={index}
                            />
                        )}
                    </div>
                );
            }
            default: {
                return null;
            }
        }
    }, [activity, handleHideRow, handleShowRow, index, isActiveDeployedItem]);

    return (
        <div key={activity.id} style={style}>
            {itemToRender}
        </div>
    );
});

ActivitiesPanelRow.displayName = "ActivitiesPanelRow";
