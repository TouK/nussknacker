import { CloudOff } from "@mui/icons-material";
import { alpha, Box, Fade, Stack, Typography } from "@mui/material";
import type { MouseEvent } from "react";
import React, { useCallback, useEffect, useMemo, useState, memo } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { Initiator, startLiveData, stopLiveData } from "../../../../actions/nk/liveData";
import type { ResultContextJson } from "../../../../http/resultsWithCountsDto";
import { getPauseReasons } from "../../../../reducers/selectors/getLiveData";
import { ContextAccordion } from "./ContextAccordion";
import { ContextTree } from "./ContextTree";
import { CountsForNodes } from "./CountsForNodes";
import { useInputOutputContext } from "./InputOutputContext";
import { useNewlyAddedContexts } from "./useNewlyAddedContexts";

export type Direction = "input" | "output";
type ValuesContextTreeProps = {
    direction?: Direction;
    onIsEmptyChange?: (value: boolean) => void;
};

export type VariableContextType = ResultContextJson & {
    nodeIds: string[];
    error?: string;
    disabled?: boolean;
};

function useVariableContext(direction: "input" | "output") {
    const {
        state: { inputDataSetId, outputDataSetId, inputVariables },
        dispatch,
        getAvailableContexts,
        inputNodesIds,
        outputNodesIds,
    } = useInputOutputContext();

    const value = useMemo(() => (direction === "input" ? inputDataSetId : outputDataSetId), [direction, inputDataSetId, outputDataSetId]);
    const [availableContexts, hiddenAvailableContexts] = useMemo(() => {
        const r = getAvailableContexts(direction);
        const contexts = r[0].sort((b, a) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());

        const pageSize = 30;
        if (contexts.length > pageSize) {
            const sliced = contexts.filter((r) => !r.disabled).slice(0, pageSize);
            return [sliced, r[1] - sliced.length];
        }

        return [contexts, r[1] - contexts.length];
    }, [direction, getAvailableContexts]);
    const enabledContexts = useMemo(() => availableContexts.filter((c) => !c.disabled), [availableContexts]);

    const [selectedContextCache, setSelectedContextCache] = useState<VariableContextType>(null);

    const liveDataPausedBy = useSelector(getPauseReasons);
    useEffect(() => {
        setSelectedContextCache((selected) => {
            if (enabledContexts.find((r) => r.id === selected?.id)) return selected;
            if (liveDataPausedBy.includes(direction === "input" ? Initiator.inputAccordion : Initiator.outputAccordion)) {
                return enabledContexts[0];
            }
            return selected;
        });
    }, [direction, enabledContexts, liveDataPausedBy]);

    const setContext = useCallback(
        (context: VariableContextType) => {
            setSelectedContextCache(context);
            dispatch({
                type: direction === "input" ? "selectInputContext" : "selectOutputContext",
                context,
            });
        },
        [direction, dispatch],
    );

    const transitionNodesIds = direction === "input" ? inputNodesIds : outputNodesIds;

    return {
        value,
        selectedContextCache,
        availableContexts,
        hiddenAvailableContexts,
        setContext,
        inputVariables,
        transitionNodesIds,
    };
}

export const VariableContextTree = memo(function ValuesContextTree({
    onIsEmptyChange,
    direction = "input",
}: ValuesContextTreeProps): JSX.Element {
    const { availableContexts, hiddenAvailableContexts, setContext, inputVariables, transitionNodesIds, selectedContextCache } =
        useVariableContext(direction);

    const isContextHighlighted = useNewlyAddedContexts(availableContexts);

    const { t } = useTranslation();
    const dispatch = useDispatch();

    useEffect(() => {
        const isEmpty = transitionNodesIds.length < 1;
        if (isEmpty) {
            dispatch(startLiveData(direction === "input" ? Initiator.inputAccordion : Initiator.outputAccordion));
        }
        onIsEmptyChange?.(isEmpty);
    }, [direction, dispatch, onIsEmptyChange, transitionNodesIds.length]);

    const showNodes = transitionNodesIds.filter((t) => t.id).length > 1;
    const toggleRefresh = useCallback(
        (e: MouseEvent) => dispatch(e.type === "mouseenter" ? stopLiveData(Initiator.list) : startLiveData(Initiator.list)),
        [dispatch],
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
            <Fade in={availableContexts.length < 1}>
                <Box
                    sx={(theme) => ({
                        position: "absolute",
                        inset: 0,
                        background: alpha(theme.palette.background.default, 0.75),
                        backdropFilter: "blur(1px)",
                    })}
                >
                    <Stack
                        direction="column"
                        sx={{
                            position: "absolute",
                            top: "50%",
                            left: "50%",
                            transform: "translate(-50%, -100%)",
                            textAlign: "center",
                            alignItems: "center",
                            opacity: 0.25,
                        }}
                    >
                        <CloudOff sx={{ fontSize: "4em" }} />
                        <Typography variant="subtitle2" noWrap>
                            {t("variableContext.noData", "data not available yet")}
                        </Typography>
                    </Stack>
                </Box>
            </Fade>
            <Stack
                spacing={1}
                sx={{
                    position: "sticky",
                    top: 0,
                    zIndex: -1,
                    padding: 1,
                }}
            >
                <Typography variant="subtitle1">{direction === "input" ? "Input variables" : "Output variables"}</Typography>
                <CountsForNodes
                    nodes={transitionNodesIds.map(({ id, totalCount }) => ({
                        id,
                        count: totalCount,
                    }))}
                    input={direction === "input"}
                />
            </Stack>

            <Box
                sx={(theme) => ({
                    background: theme.palette.background.paper,
                })}
            >
                {(!selectedContextCache || availableContexts.find((r) => r.id === selectedContextCache.id)
                    ? availableContexts
                    : [...availableContexts, selectedContextCache]
                ).map((r, index) => (
                    <ContextAccordion
                        key={r.id}
                        value={r}
                        direction={direction}
                        disabled={r.disabled}
                        expanded={selectedContextCache?.id === r.id && !r.disabled}
                        onToggle={setContext}
                        locked={index >= availableContexts.length}
                        showNodes={showNodes}
                        newlyAdded={isContextHighlighted(r.id)}
                    >
                        {direction === "output" ? <>{r.error}</> : null}
                        <ContextTree context={r} oldFields={direction === "output" ? inputVariables : []} />
                    </ContextAccordion>
                ))}
            </Box>

            {hiddenAvailableContexts > 0 ? (
                <Typography
                    variant="body2"
                    sx={{
                        padding: 2,
                        textAlign: "right",
                        opacity: 0.5,
                        position: "sticky",
                        bottom: 0,
                        zIndex: -1,
                    }}
                >
                    {t("variableContext.truncatedList", "...and {{count}} more", { count: hiddenAvailableContexts })}
                </Typography>
            ) : null}
        </Box>
    );
});
