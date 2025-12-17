import { Box, Fade, Typography } from "@mui/material";
import type { MouseEvent } from "react";
import React, { memo, useCallback, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { Initiator, startLiveData, stopLiveData } from "../../../../actions/nk/liveData";
import type { ResultContextJson } from "../../../../http/resultsWithCountsDto";
import { getIsTestingMode } from "../../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { ContextData } from "./ContextData";
import { EmptyListIndicator } from "./EmptyListIndicator";
import { LiveDataLoadingIndicator } from "./LiveDataLoadingIndicator";
import { useVariableContext } from "./useVariableContext";
import { VariableContextTreeHeader } from "./VariableContextTreeHeader";

export type Direction = "input" | "output";
export type ValuesContextTreeProps = {
    direction?: Direction;
    onIsEmptyChange?: (value: boolean) => void;
};

export type VariableContextType = ResultContextJson & {
    nodeIds: string[];
    error?: string;
    disabled?: boolean;
};

export const VariableContextTree = memo(function ValuesContextTree({
    onIsEmptyChange,
    direction = "input",
}: ValuesContextTreeProps): React.JSX.Element {
    const { availableContexts, hiddenAvailableContexts, setContext, inputVariables, transitionNodesIds, selectedContextCache } =
        useVariableContext(direction);

    const { t } = useTranslation();
    const dispatch = useAppDispatch();
    const isTestingMode = useAppSelector(getIsTestingMode);

    useEffect(() => {
        const isEmpty = transitionNodesIds.length < 1;
        if (isEmpty && !isTestingMode) {
            dispatch(startLiveData(direction === "input" ? Initiator.inputAccordion : Initiator.outputAccordion));
        }
        onIsEmptyChange?.(isEmpty);
    }, [direction, dispatch, isTestingMode, onIsEmptyChange, transitionNodesIds.length]);

    const showNodes = transitionNodesIds.length > 1;
    const toggleRefresh = useCallback(
        (e: MouseEvent) => {
            const isEmpty = transitionNodesIds.flatMap(({ results = [] }) => results).length < 1;
            if (!isEmpty && !isTestingMode) {
                return dispatch(e.type === "mouseenter" ? stopLiveData(Initiator.list) : startLiveData(Initiator.list));
            }
        },
        [dispatch, isTestingMode, transitionNodesIds],
    );

    const data = useMemo(
        () =>
            !selectedContextCache || availableContexts.find((r) => r.id === selectedContextCache.id)
                ? availableContexts
                : [...availableContexts, selectedContextCache],
        [availableContexts, selectedContextCache],
    );

    return (
        <Box
            sx={{
                width: "100%",
                height: "100%",
                minWidth: 220,
            }}
            onMouseEnter={toggleRefresh}
            onMouseLeave={toggleRefresh}
        >
            <Fade in={availableContexts.length < 1} timeout={1000} mountOnEnter unmountOnExit>
                <EmptyListIndicator />
            </Fade>
            <VariableContextTreeHeader direction={direction} transitionNodesIds={transitionNodesIds} />
            <ContextData
                data={data}
                direction={direction}
                selectedContextCache={selectedContextCache?.id}
                setContext={setContext}
                availableContexts={availableContexts.length}
                showNodes={showNodes}
                inputVariables={inputVariables}
            >
                <LiveDataLoadingIndicator noLabel={direction === "input"} />
            </ContextData>
            {hiddenAvailableContexts > 0 ? (
                <Typography
                    variant="body2"
                    sx={{
                        padding: 2,
                        textAlign: "right",
                        opacity: 0.5,
                        position: "sticky",
                        bottom: 0,
                        zIndex: 0,
                    }}
                >
                    {t("variableContext.truncatedList", "...and {{count}} more", { count: hiddenAvailableContexts })}
                </Typography>
            ) : null}
        </Box>
    );
});
