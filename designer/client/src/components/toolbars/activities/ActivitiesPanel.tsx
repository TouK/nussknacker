import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { ActionMetadata, ActivitiesResponse, ActivityMetadata } from "../../../http/HttpService";
import { VariableSizeList } from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";
import { ActivitiesPanelRow } from "./ActivitiesPanelRow";
import { alpha, Box, CircularProgress, styled } from "@mui/material";
import { useActivitiesSearch } from "./useActivitiesSearch";
import { ActivitiesSearch } from "./ActivitiesSearch";
import { blendLighten } from "../../../containers/theme/helpers";
import { ActivitiesPanelFooter } from "./ActivitiesPanelFooter";
import { useDispatch, useSelector } from "react-redux";
import { getActivities, getProcessName } from "../../../reducers/selectors/graph";
import { getScenarioActivities, updateScenarioActivities } from "../../../actions/nk/scenarioActivities";

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
    value: string | [string, string];
};

export type UIActivity = ItemActivity | ButtonActivity | DateActivity;

const estimatedItemSize = 150;

export const ActivitiesPanel = (props: ToolbarPanelProps) => {
    const listRef = useRef<VariableSizeList>(null);
    const rowHeights = useRef({});
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const scenarioName = useSelector(getProcessName);
    const data = useSelector(getActivities);

    const dispatch = useDispatch();

    const setRowHeight = useCallback((index: number, height: number) => {
        if (listRef.current) {
            listRef.current.resetAfterIndex(0);
        }

        rowHeights.current = { ...rowHeights.current, [index]: height };
    }, []);

    const getRowHeight = useCallback((index: number) => {
        return rowHeights.current[index] || estimatedItemSize;
    }, []);

    const { handleSearch, foundResults, selectedResult, searchQuery, changeResult, handleClearResults } = useActivitiesSearch({
        activities: data,
        handleScrollToItem: (index, align) => listRef.current.scrollToItem(index, align),
    });

    const handleHideRow = (index: number, sameItemOccurrence: number) => {
        dispatch(
            updateScenarioActivities((prevState) => {
                return prevState.map((data, prevStateItemIndex) => {
                    if (prevStateItemIndex === index) {
                        return { ...data, isClicked: false };
                    }

                    if (prevStateItemIndex <= prevStateItemIndex && prevStateItemIndex > index - sameItemOccurrence - 1) {
                        return { ...data, isHidden: true };
                    }

                    return data;
                });
            }),
        );
        listRef.current.scrollToItem(index - sameItemOccurrence - 2);
    };

    const handleShowRow = (index: number, sameItemOccurrence: number) => {
        dispatch(
            updateScenarioActivities((prevState) => {
                return prevState.map((data, prevStateItemIndex) => {
                    if (prevStateItemIndex === index + sameItemOccurrence) {
                        return { ...data, isClicked: true };
                    }

                    if (prevStateItemIndex >= index && prevStateItemIndex < index + sameItemOccurrence) {
                        return { ...data, isHidden: false };
                    }

                    return data;
                });
            }),
        );
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
            await dispatch(await getScenarioActivities(scenarioName));
        } finally {
            setIsLoading(false);
        }
    }, [dispatch, scenarioName]);

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
            <ActivitiesPanelFooter />
        </ToolbarWrapper>
    );
};
