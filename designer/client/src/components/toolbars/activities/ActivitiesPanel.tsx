import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import httpService, { ActionMetadata, ActivitiesResponse, ActivityMetadata, ActivityMetadataResponse } from "../../../http/HttpService";
import { VariableSizeList } from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";
import moment from "moment";
import { v4 as uuid4 } from "uuid";
import { ActivitiesPanelRow } from "./ActivitiesPanelRow";
import { alpha, Box, CircularProgress, styled } from "@mui/material";
import { UseActivitiesSearch } from "./useActivitiesSearch";
import { ActivitiesSearch } from "./ActivitiesSearch";
import { blendLighten } from "../../../containers/theme/helpers";
import { ActivitiesPanelFooter } from "./ActivitiesPanelFooter";

const StyledVariableSizeList = styled(VariableSizeList)(({ theme }) => ({
    "::-webkit-scrollbar": {
        width: "5px",
        height: "0",
    },
    "::-webkit-scrollbar-track": {
        background: blendLighten(theme.palette.background.paper, 0.5),
    },
    "::-webkit-scrollbar-thumb": {
        background: alpha(theme.palette.background.paper, 0.85),
    },
    "::-webkit-scrollbar-thumb:hover": {
        background: alpha(theme.palette.background.paper, 0.85),
    },
}));

export type Activity = ActivitiesResponse["activities"][number] & {
    activities: ActivityMetadata;
    actions: ActionMetadata[];
};

export type ItemActivity = Activity & {
    uiGeneratedId: string;
    uiType: "item";
    isHidden: boolean;
    isFound: boolean;
    isActiveFound: boolean;
};

export type ButtonActivity = {
    uiGeneratedId: string;
    uiType: "moreItemsButton";
    sameItemOccurrence: number;
    isClicked: boolean;
};

export type DateActivity = {
    uiGeneratedId: string;
    uiType: "date";
    value: string;
};

export type UIActivities = ItemActivity | ButtonActivity | DateActivity;

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

const extendActivitiesWithUIData = (activitiesDataWithMetadata: Activity[]) => {
    const infiniteListData: UIActivities[] = [];
    const hideItemsOptionAvailableLimit = 4;

    function formatDate(date: string) {
        const now = moment(); // Current date and time
        const inputDate = moment(date); // Date to be formatted

        if (inputDate.isSame(now, "day")) {
            return "Today";
        } else if (inputDate.isSame(moment().subtract(1, "days"), "day")) {
            return "Yesterday";
        } else {
            return inputDate.format("YYYY-MM-DD");
        }
    }

    const recursiveDateLabelDesignation = (activity: Activity, index: number, occurrence = 0): DateActivity | undefined => {
        const nextActivity = activitiesDataWithMetadata[index + 1 + occurrence];
        const previousActivity = activitiesDataWithMetadata[index - 1 + occurrence];

        if (occurrence > hideItemsOptionAvailableLimit && activity.type !== nextActivity?.type) {
            return {
                uiGeneratedId: uuid4(),
                uiType: "date",
                value: `${formatDate(previousActivity.date)} - ${formatDate(activity.date)}`,
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
                uiGeneratedId: uuid4(),
                uiType: "date",
                value: formatDate(activity.date),
            };
        }

        return undefined;
    };

    const recursiveMoreItemsButtonDesignation = (activity: Activity, index: number, occurrence = 0): ButtonActivity | undefined => {
        const previousActivityIndex = index - 1 - occurrence;
        const previousActivity = activitiesDataWithMetadata[previousActivityIndex];
        if (occurrence > hideItemsOptionAvailableLimit && activity.type !== previousActivity?.type) {
            return {
                uiGeneratedId: uuid4(),
                uiType: "moreItemsButton",
                sameItemOccurrence: occurrence,
                isClicked: false,
            };
        }

        if (activity.type === previousActivity?.type) {
            occurrence++;
            return recursiveMoreItemsButtonDesignation(activity, index, occurrence);
        }

        return undefined;
    };

    const initiallyHideItems = () => {
        for (let i = infiniteListData.length - 1 - hideItemsOptionAvailableLimit; i < infiniteListData.length; i++) {
            const item = infiniteListData[i];

            if (item.uiType === "item") {
                item.isHidden = true;
            }
        }
    };

    activitiesDataWithMetadata
        .sort((a, b) => moment(b.date).diff(a.date))
        .forEach((activity, index) => {
            const dateLabel = recursiveDateLabelDesignation(activity, index);
            const moreItemsButton = recursiveMoreItemsButtonDesignation(activity, index);
            dateLabel && infiniteListData.push(dateLabel);
            infiniteListData.push({
                ...activity,
                isActiveFound: false,
                isFound: false,
                uiGeneratedId: uuid4(),
                uiType: "item",
                isHidden: false,
            });
            if (moreItemsButton) {
                initiallyHideItems();
                infiniteListData.push(moreItemsButton);
            }
        });

    return infiniteListData;
};

export const ActivitiesPanel = (props: ToolbarPanelProps) => {
    const listRef = useRef<VariableSizeList>(null);
    const rowHeights = useRef({});
    const [isLoading, setIsLoading] = useState<boolean>(true);

    const setRowHeight = useCallback((index: number, height: number) => {
        if (listRef.current) {
            listRef.current.resetAfterIndex(0);
        }

        rowHeights.current = { ...rowHeights.current, [index]: height };
    }, []);

    const getRowHeight = useCallback((index: number) => {
        return rowHeights.current[index] || estimatedItemSize;
    }, []);

    const [data, setData] = useState<UIActivities[]>([]);
    const { handleSearch, foundResults, selectedResult, searchQuery, changeResult, handleClearResults } = UseActivitiesSearch({
        activities: data,
        listRef,
    });

    const handleHideRow = (index: number, sameItemOccurrence: number) => {
        setData((prevState) => {
            return prevState.map((data, indx) => {
                if (indx === index) {
                    return { ...data, isClicked: false };
                }

                if (indx <= index && indx > index - sameItemOccurrence - 1) {
                    return { ...data, isHidden: true };
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
                    return { ...data, isClicked: true };
                }

                if (indx >= index && indx < index + sameItemOccurrence) {
                    return { ...data, isHidden: false };
                }

                return data;
            });
        });
    };

    const dataToDisplay = useMemo(
        () =>
            data
                .filter((activity) => (activity.uiType === "item" && !activity.isHidden) || activity.uiType !== "item")
                .map((activity) => {
                    if (activity.uiType !== "item") {
                        return activity;
                    }

                    activity.isFound = false;
                    activity.isActiveFound = false;

                    if (foundResults.some((foundResult) => foundResult === activity.uiGeneratedId)) {
                        activity.isFound = true;
                    }

                    if (activity.uiGeneratedId === foundResults[selectedResult]) {
                        activity.isActiveFound = true;
                    }

                    return activity;
                }),
        [data, foundResults, selectedResult],
    );

    const handleFetchActivities = useCallback(async () => {
        setIsLoading(true);
        try {
            const [activitiesMetadata, { activities }] = await Promise.all([
                httpService.fetchActivitiesMetadata(),
                httpService.fetchActivities(),
            ]);
            const mergedActivitiesDataWithMetadata = mergeActivityDataWithMetadata(activities, activitiesMetadata);

            setData(extendActivitiesWithUIData(mergedActivitiesDataWithMetadata));
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        handleFetchActivities();
    }, [handleFetchActivities]);

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
            <Box
                width={"100%"}
                height={"500px"}
                mt={1}
                sx={() => ({
                    "::-webkit-scrollbar": {
                        width: "5px",
                        height: "0",
                    },
                    "::-webkit-scrollbar-track": {
                        background: "red",
                    },
                    "::-webkit-scrollbar-thumb": {
                        background: "red",
                    },
                    "::-webkit-scrollbar-thumb:hover": {
                        background: "red",
                    },
                })}
            >
                {isLoading ? (
                    <Box display={"flex"} justifyContent={"center"} height={"100%"} alignItems={"center"}>
                        <CircularProgress />
                    </Box>
                ) : (
                    <AutoSizer>
                        {({ width, height }) => (
                            <StyledVariableSizeList
                                ref={listRef}
                                itemCount={dataToDisplay.length}
                                itemSize={getRowHeight}
                                height={height}
                                width={width}
                                estimatedItemSize={estimatedItemSize}
                                itemKey={(index) => {
                                    return dataToDisplay[index].uiGeneratedId;
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
                                        searchQuery={searchQuery}
                                    />
                                )}
                            </StyledVariableSizeList>
                        )}
                    </AutoSizer>
                )}
            </Box>
            <ActivitiesPanelFooter handleFetchActivities={handleFetchActivities} />
        </ToolbarWrapper>
    );
};
