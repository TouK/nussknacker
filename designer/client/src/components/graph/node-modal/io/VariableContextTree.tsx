import { Box, Stack, Typography } from "@mui/material";
import React, { memo, useCallback, useEffect, useMemo } from "react";
import { useSelector } from "react-redux";
import { Context } from "../../../../common/TestResultUtils";
import { getProcessCounts } from "../../../../reducers/selectors/graph";
import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { ContextAccordion } from "./ContextAccordion";
import { ContextTitle } from "./ContextTitle";
import { useInputOutputContext } from "./InputOutputContext";
import { NextNodesCounts } from "./NextNodesCounts";
import { ContextTree } from "./ContextTree";

type ValuesContextTreeProps = {
    direction?: "input" | "output";
};

export type VariableContextType = Context & {
    nodeIds: string[];
    error?: string;
    disabled?: boolean;
};

function useVariableContext(direction: "input" | "output") {
    const {
        state: { inputDataSetId, outputDataSetId, inputVariables },
        dispatch,
        getAvailableContexts,
        inputNodes,
        outputNodes,
        prevNodes,
    } = useInputOutputContext();

    const value = useMemo(() => (direction === "input" ? inputDataSetId : outputDataSetId), [direction, inputDataSetId, outputDataSetId]);
    const nodeIds = useMemo(() => (direction === "input" ? inputNodes : outputNodes), [direction, inputNodes, outputNodes]);
    const [availableContexts, hiddenAvailableContexts] = useMemo(() => {
        const contexts = getAvailableContexts(nodeIds, direction);
        const pageSize = 30;
        if (contexts.length > pageSize) {
            const sliced = contexts
                // .sort((a, b) => (b.disabled ? 0 : 1) - (a.disabled ? 0 : 1))
                .filter((r) => !r.disabled)
                .slice(0, pageSize);
            return [sliced, contexts.length - sliced.length];
        }
        return [contexts, 0];
    }, [direction, getAvailableContexts, nodeIds]);

    const setContext = useCallback(
        (context: VariableContextType) =>
            dispatch({
                type: direction === "input" ? "selectInputContext" : "selectOutputContext",
                context,
            }),
        [direction, dispatch],
    );

    return {
        value,
        nodeIds,
        availableContexts,
        hiddenAvailableContexts,
        setContext,
        inputVariables,
        prevNodes,
    };
}

export const VariableContextTree = memo(function ValuesContextTree({ direction = "input" }: ValuesContextTreeProps): JSX.Element {
    const { value, nodeIds, availableContexts, hiddenAvailableContexts, setContext, inputVariables, prevNodes } =
        useVariableContext(direction);

    useEffect(() => {
        const enabled = availableContexts.filter(({ disabled }) => !disabled);
        if (enabled.length <= 0) return;
        if (value === null) return;
        if (enabled.find(({ id }) => id === value)) return;
        setContext(enabled[0]);
    }, [availableContexts, direction, setContext, value]);

    const processCounts = useSelector(getProcessCounts);
    const userSettings = useSelector(getUserSettings);
    const shortCounts = userSettings["node.shortCounts"];

    return (
        <Box
            sx={{
                width: "100%",
                minWidth: 300,
            }}
        >
            <Stack
                padding={1}
                sx={{
                    position: "sticky",
                    top: 0,
                    zIndex: -1,
                }}
            >
                <Typography variant="subtitle1">{direction}</Typography>
                <NextNodesCounts
                    nodes={(direction === "input" ? prevNodes : nodeIds).map((id) => ({
                        id,
                        count: processCounts[id]?.all,
                    }))}
                    input={direction === "input"}
                />
            </Stack>

            <Box
                sx={(theme) => ({
                    background: theme.palette.background.paper,
                })}
            >
                {availableContexts.map((r) => (
                    <ContextAccordion
                        key={r.id}
                        disabled={r.disabled}
                        expanded={value === r.id && !r.disabled}
                        onToggle={() => setContext(r)}
                        title={<ContextTitle context={r} showNodes={nodeIds.length > 1} />}
                    >
                        {direction === "output" ? <>{r.error}</> : null}
                        <ContextTree context={r} oldFields={inputVariables} />
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
                    ...and {hiddenAvailableContexts} more
                </Typography>
            ) : null}
        </Box>
    );
});
