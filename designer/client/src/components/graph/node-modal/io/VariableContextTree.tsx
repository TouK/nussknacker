import { Box, Fade, Typography } from "@mui/material";
import type { MouseEvent } from "react";
import React, { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { Initiator, startLiveData, stopLiveData } from "../../../../actions/nk/liveData";
import type { ResultContextJson } from "../../../../http/resultsWithCountsDto";
import { useAppDispatch } from "../../../../store/storeHelpers";
import { ContextData } from "./ContextData";
import { EmptyListIndicator } from "./EmptyListIndicator";
import { useFilterContext } from "./FilterContext";
import { LiveDataLoadingIndicator } from "./LiveDataLoadingIndicator";
import { useVariableContext } from "./useVariableContext";
import { VariableContextTreeHeader } from "./VariableContextTreeHeader";

export type Direction = "input" | "output";
export type ValuesContextTreeProps = {
    direction?: Direction;
    onIsEmptyChange?: (value: boolean) => void;
    paused?: boolean;
};

export type VariableContextType = ResultContextJson & {
    nodeIds: string[];
    error?: string;
    disabled?: boolean;
};

export const VariableContextTree = memo(function ValuesContextTree({
    onIsEmptyChange,
    direction = "input",
    paused,
}: ValuesContextTreeProps): React.JSX.Element {
    const { filter: nodeIdFilter } = useFilterContext();

    const { availableContexts, hiddenAvailableContexts, setContext, inputVariables, transitionNodesIds, selectedContextCache } =
        useVariableContext(direction, nodeIdFilter);

    const { t } = useTranslation();
    const dispatch = useAppDispatch();

    const [isEmpty, setIsEmpty] = useState(true);
    useEffect(() => {
        setIsEmpty(transitionNodesIds.length < 1 && nodeIdFilter.length < 1);
    }, [nodeIdFilter.length, transitionNodesIds.length]);

    useEffect(() => {
        if (isEmpty) {
            dispatch(startLiveData(direction === "input" ? Initiator.inputAccordion : Initiator.outputAccordion));
        }
        onIsEmptyChange?.(isEmpty);
    }, [direction, dispatch, isEmpty, onIsEmptyChange]);

    const showNodes = transitionNodesIds.length > 1;
    const toggleRefresh = useCallback(
        (e: MouseEvent) => {
            const isEmpty = transitionNodesIds.flatMap(({ results = [] }) => results).length < 1;
            if (!isEmpty) {
                return dispatch(e.type === "mouseenter" && !paused ? stopLiveData(Initiator.list) : startLiveData(Initiator.list));
            }
        },
        [paused, dispatch, transitionNodesIds],
    );

    const dataRef = useRef<VariableContextType[]>([]);
    const data = useMemo(() => {
        if (!paused) {
            dataRef.current =
                !selectedContextCache || availableContexts.find((r) => r.id === selectedContextCache.id)
                    ? availableContexts
                    : [...availableContexts, selectedContextCache];
        }
        return dataRef.current;
    }, [availableContexts, paused, selectedContextCache]);

    return (
        <Box
            sx={{
                width: "100%",
                height: "100%",
                minWidth: 220,
                display: "flex",
                flexDirection: "column",
                justifyContent: "flex-start",
            }}
            onMouseEnter={toggleRefresh}
            onMouseLeave={toggleRefresh}
        >
            <Fade in={availableContexts.length < 1 && nodeIdFilter.length < 1} timeout={1000} mountOnEnter unmountOnExit>
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
                {paused ? null : <LiveDataLoadingIndicator noLabel={direction === "input"} />}
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
