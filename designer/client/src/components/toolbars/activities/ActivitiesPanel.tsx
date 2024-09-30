import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import httpService, { ActionMetadata, ActivitiesResponse, ActivityMetadata } from "../../../http/HttpService";
import { VariableSizeList } from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";
import { ActivitiesPanelRow } from "./ActivitiesPanelRow";
import { alpha, Box, CircularProgress, styled } from "@mui/material";
import { useActivitiesSearch } from "./useActivitiesSearch";
import { ActivitiesSearch } from "./ActivitiesSearch";
import { blendLighten } from "../../../containers/theme/helpers";
import { ActivitiesPanelFooter } from "./ActivitiesPanelFooter";
import { useSelector } from "react-redux";
import { getProcessName } from "../../../reducers/selectors/graph";
import { extendActivitiesWithUIData } from "./helpers/extendActivitiesWithUIData";
import { mergeActivityDataWithMetadata } from "./helpers/mergeActivityDataWithMetadata";

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
    uiType: "toggleItemsButton";
    sameItemOccurrence: number;
    isClicked: boolean;
};

export type DateActivity = {
    uiGeneratedId: string;
    uiType: "date";
    value: string;
};

export type UIActivity = ItemActivity | ButtonActivity | DateActivity;

const estimatedItemSize = 150;

export const ActivitiesPanel = (props: ToolbarPanelProps) => {
    const listRef = useRef<VariableSizeList>(null);
    const rowHeights = useRef({});
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const scenarioName = useSelector(getProcessName);

    const setRowHeight = useCallback((index: number, height: number) => {
        if (listRef.current) {
            listRef.current.resetAfterIndex(0);
        }

        rowHeights.current = { ...rowHeights.current, [index]: height };
    }, []);

    const getRowHeight = useCallback((index: number) => {
        return rowHeights.current[index] || estimatedItemSize;
    }, []);

    const [data, setData] = useState<UIActivity[]>([]);
    const { handleSearch, foundResults, selectedResult, searchQuery, changeResult, handleClearResults } = useActivitiesSearch({
        activities: data,
        handleScrollToItem: (index, align) => listRef.current.scrollToItem(index, align),
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
            const [
                { data: activitiesMetadata },
                {
                    data: { activities },
                },
            ] = await Promise.all([httpService.fetchActivitiesMetadata(scenarioName), httpService.fetchActivities(scenarioName)]);

            const mergedActivitiesDataWithMetadata = mergeActivityDataWithMetadata(activities, activitiesMetadata);

            setData(extendActivitiesWithUIData(mergedActivitiesDataWithMetadata));
        } finally {
            setIsLoading(false);
        }
    }, [scenarioName]);

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
