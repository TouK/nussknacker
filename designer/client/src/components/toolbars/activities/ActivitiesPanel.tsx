import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import httpService, { ActionMetadata, ActivitiesResponse, ActivityMetadata, ActivityMetadataResponse } from "../../../http/HttpService";
import { VariableSizeList } from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";
import moment from "moment";
import { v4 as uuid4 } from "uuid";
import { ActivitiesPanelRow } from "./ActivitiesPanelRow";
import { Box } from "@mui/material";
import { UseActivitiesSearch } from "./useActivitiesSearch";
import { ActivitiesSearch } from "./ActivitiesSearch";

interface UiButtonActivity {
    type: "moreItemsButton";
    sameItemOccurrence: number;
    isClicked: boolean;
}

interface UiDateActivity {
    type: "date";
    value: string;
}

export interface UiItemActivity {
    type: "item";
    isDisabled: boolean;
    isFound: boolean;
    isActiveFound: boolean;
}

export type Activity<T = UiButtonActivity | UiDateActivity | UiItemActivity> = ActivitiesResponse["activities"][number] & {
    activities: ActivityMetadata;
    actions: ActionMetadata[];
    ui?: T;
};

const estimatedItemSize = 150;
const mergeActivityDataWithMetadata = (
    activities: ActivitiesResponse["activities"],
    activitiesMetadata: ActivityMetadataResponse,
): Activity[] => {
    return activities.map((activity): Activity => {
        const activities = activitiesMetadata.activities.find((activityMetadata) => activityMetadata.type === activity.type);
        const actions = activities.supportedActions.map((supportedAction) => {
            return activitiesMetadata.actions.find((action) => action.id === supportedAction);
        });

        return { ...activity, activities, actions };
    });
};

const handleDataToDisplayGeneration = (activitiesDataWithMetadata: Activity[]) => {
    const infiniteListData = [];
    const hideItemsOptionAvailableLimit = 4;
    const formatDate = (date: string) => moment(date).format("YYYY-MM-DD");

    const recursiveDateLabelDesignation = (activity: Activity, index: number, occurrence = 0) => {
        const nextActivity = activitiesDataWithMetadata[index + 1 + occurrence];
        const previousActivity = activitiesDataWithMetadata[index - 1 + occurrence];

        if (occurrence > hideItemsOptionAvailableLimit && activity.type !== nextActivity?.type) {
            return {
                id: uuid4(),
                ui: {
                    type: "date",
                    value: `${formatDate(previousActivity.date)} - ${formatDate(activity.date)}`,
                },
            };
        }

        if (activity.type === nextActivity?.type) {
            occurrence++;
            return recursiveDateLabelDesignation(activity, index, occurrence);
        }

        if (
            activity.type !== nextActivity?.type &&
            moment(activity.date).format("YYYY-MM-DD") !==
                (previousActivity?.date ? moment(previousActivity.date).format("YYYY-MM-DD") : undefined)
        ) {
            return {
                id: uuid4(),
                ui: { value: formatDate(activity.date), type: "date" },
            };
        }

        return undefined;
    };

    const recursiveMoreItemsButtonDesignation = (activity: Activity, index: number, occurrence = 0) => {
        const previousActivity = activitiesDataWithMetadata[index - 1 - occurrence];

        if (occurrence > hideItemsOptionAvailableLimit && activity.type !== previousActivity?.type) {
            return {
                id: uuid4(),
                ui: {
                    type: "moreItemsButton",
                    sameItemOccurrence: occurrence,
                    clicked: false,
                },
            };
        }

        if (activity.type === previousActivity?.type) {
            occurrence++;
            return recursiveMoreItemsButtonDesignation(activity, index, occurrence);
        }

        return undefined;
    };

    activitiesDataWithMetadata
        .sort((a, b) => moment(b.date).diff(a.date))
        .forEach((activity, index) => {
            const dateLabel = recursiveDateLabelDesignation(activity, index);
            const moreItemsButton = recursiveMoreItemsButtonDesignation(activity, index);
            dateLabel && infiniteListData.push(dateLabel);
            infiniteListData.push({ ...activity, id: uuid4(), ui: { type: "item", isDisabled: false } });
            moreItemsButton && infiniteListData.push(moreItemsButton);
        });

    return infiniteListData;
};

export const ActivitiesPanel = (props: ToolbarPanelProps) => {
    const listRef = useRef<VariableSizeList>(null);
    const rowHeights = useRef({});

    const setRowHeight = useCallback((index: number, height: number) => {
        if (listRef.current) {
            listRef.current.resetAfterIndex(0);
        }

        rowHeights.current = { ...rowHeights.current, [index]: height };
    }, []);

    const getRowHeight = useCallback((index: number) => {
        return rowHeights.current[index] || estimatedItemSize;
    }, []);

    const [data, setData] = useState<Activity[]>([]);
    const { handleSearch, foundResults, selectedResult, searchQuery, changeResult, handleClearResults } = UseActivitiesSearch({
        activities: data,
        listRef,
    });

    const handleHideRow = (index: number, sameItemOccurrence: number) => {
        setData((prevState) => {
            return prevState.map((data, indx) => {
                if (indx === index) {
                    return { ...data, ui: { ...data.ui, isClicked: true } };
                }

                if (indx <= index && indx > index - sameItemOccurrence - 1) {
                    return { ...data, ui: { ...data.ui, isDisabled: true } };
                }

                return data;
            });
        });
        listRef.current.scrollToItem(index - sameItemOccurrence - 2);
    };

    const handleShowRow = (index: number, sameItemOccurrence: number) => {
        setData((prevState) => {
            return prevState.map((data, indx) => {
                if (indx === index + sameItemOccurrence) {
                    return { ...data, ui: { ...data.ui, isClicked: false } };
                }

                if (indx >= index && indx < index + sameItemOccurrence) {
                    return { ...data, ui: { ...data.ui, isDisabled: false } };
                }

                return data;
            });
        });
    };

    const dataToDisplay = useMemo(
        () =>
            data
                .filter((activity) => (activity.ui.type === "item" && !activity.ui.isDisabled) || activity.ui.type !== "item")
                .map((activity, index) => {
                    if (activity.ui.type !== "item") {
                        return activity;
                    }

                    activity.ui.isFound = false;
                    activity.ui.isActiveFound = false;

                    if (foundResults.some((foundResult) => foundResult === activity.id)) {
                        activity.ui.isFound = true;
                    }

                    if (activity.id === foundResults[selectedResult]) {
                        activity.ui.isActiveFound = true;
                    }

                    return activity;
                }),
        [data, foundResults, selectedResult],
    );

    useEffect(() => {
        Promise.all([httpService.fetchActivitiesMetadata(), httpService.fetchActivities()]).then(([activitiesMetadata, { activities }]) => {
            const mergedActivitiesDataWithMetadata = mergeActivityDataWithMetadata(activities, activitiesMetadata);

            setData(handleDataToDisplayGeneration(mergedActivitiesDataWithMetadata));
        });
    }, []);

    if (!dataToDisplay.length) return;

    return (
        <ToolbarWrapper {...props} title={"Activities"}>
            <ActivitiesSearch
                handleSearch={handleSearch}
                changeResult={changeResult}
                foundResults={foundResults}
                selectedResult={selectedResult}
                searchQuery={searchQuery}
                handleClearResults={handleClearResults}
            />
            <Box width={"100%"} height={"700px"} mt={1}>
                <AutoSizer>
                    {({ width, height }) => (
                        <VariableSizeList
                            ref={listRef}
                            itemCount={dataToDisplay.length}
                            itemSize={getRowHeight}
                            height={height}
                            width={width}
                            estimatedItemSize={estimatedItemSize}
                            itemKey={(index) => {
                                return dataToDisplay[index].id;
                            }}
                        >
                            {({ index, style }) => (
                                <ActivitiesPanelRow
                                    index={index}
                                    style={style}
                                    setRowHeight={setRowHeight}
                                    handleShowRow={handleShowRow}
                                    handleHideRow={handleHideRow}
                                    activities={dataToDisplay}
                                />
                            )}
                        </VariableSizeList>
                    )}
                </AutoSizer>
            </Box>
        </ToolbarWrapper>
    );
};
