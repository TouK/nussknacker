import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { ActionMetadata, ActivitiesResponse, ActivityMetadata } from "../../../http/HttpService";
import { VariableSizeList } from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";
import { ActivitiesPanelRow } from "./ActivitiesPanelRow";
import { Box, CircularProgress, styled } from "@mui/material";
import { useActivitiesSearch } from "./useActivitiesSearch";
import { ActivitiesSearch } from "./ActivitiesSearch";
import { blendDarken, blendLighten } from "../../../containers/theme/helpers";
import { ActivitiesPanelFooter } from "./ActivitiesPanelFooter";
import { useDispatch, useSelector } from "react-redux";
import { getProcessName } from "../../../reducers/selectors/graph";
import { getScenarioActivities, updateScenarioActivities } from "../../../actions/nk/scenarioActivities";
import { getActivities } from "../../../reducers/selectors/activities";
import { handleToggleActivities } from "./helpers/handleToggleActivities";

const StyledVariableSizeList = styled(VariableSizeList)(({ theme }) => ({
    "::-webkit-scrollbar": {
        width: "5px",
        height: "0",
    },
    "::-webkit-scrollbar-track": {
        background: blendDarken(theme.palette.common.white, 0.75),
    },
    "::-webkit-scrollbar-thumb": {
        background: blendLighten(theme.palette.background.paper, 0.5),
    },
    "::-webkit-scrollbar-thumb:hover": {
        background: blendLighten(theme.palette.background.paper, 0.5),
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
const panelHeight = "500px";

export const ActivitiesPanel = (props: ToolbarPanelProps) => {
    const listRef = useRef<VariableSizeList>(null);

    /*
     * It's for a calculation of dynamic items size https://github.com/bvaughn/react-window/issues/582
     **/
    const rowHeights = useRef({});
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const scenarioName = useSelector(getProcessName);
    const uiActivities = useSelector(getActivities);

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

    const handleUpdateScenarioActivities = useCallback(
        (activities: (activities: UIActivity[]) => UIActivity[]) => dispatch(updateScenarioActivities(activities)),
        [dispatch],
    );
    const { handleSearch, foundResults, selectedResult, searchQuery, changeResult, handleClearResults } = useActivitiesSearch({
        activities: uiActivities,
        handleScrollToItem: (index, align) => listRef.current.scrollToItem(index, align),
        handleUpdateScenarioActivities,
    });

    const handleHideRows = (uiGeneratedId: string, sameItemOccurrence: number) => {
        dispatch(
            updateScenarioActivities((prevState) => {
                const { uiActivities, buttonPosition } = handleToggleActivities(prevState, uiGeneratedId, sameItemOccurrence);
                listRef.current.scrollToItem(buttonPosition - 2);
                return uiActivities;
            }),
        );
    };

    const handleShowRows = (uiGeneratedId: string, sameItemOccurrence: number) => {
        dispatch(
            updateScenarioActivities((prevState) => {
                const { uiActivities } = handleToggleActivities(prevState, uiGeneratedId, sameItemOccurrence);
                return uiActivities;
            }),
        );
    };

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

    /*
     * To correctly display items in a react-window list, only the visible elements should be passed.
     **/
    const visibleUiActivities = useMemo(
        () => uiActivities.filter((activity) => (activity.uiType === "item" && !activity.isHidden) || activity.uiType !== "item"),
        [uiActivities],
    );

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
            <Box width={"100%"} height={panelHeight} mt={1}>
                {isLoading ? (
                    <Box display={"flex"} justifyContent={"center"} height={"100%"} alignItems={"center"}>
                        <CircularProgress />
                    </Box>
                ) : (
                    <AutoSizer>
                        {({ width, height }) => (
                            <StyledVariableSizeList
                                ref={listRef}
                                itemCount={visibleUiActivities.length}
                                itemSize={getRowHeight}
                                height={height}
                                width={width}
                                estimatedItemSize={estimatedItemSize}
                                itemKey={(index) => {
                                    return visibleUiActivities[index].uiGeneratedId;
                                }}
                            >
                                {({ index, style }) => (
                                    <ActivitiesPanelRow
                                        index={index}
                                        style={style}
                                        setRowHeight={setRowHeight}
                                        handleShowRows={handleShowRows}
                                        handleHideRows={handleHideRows}
                                        activities={visibleUiActivities}
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
