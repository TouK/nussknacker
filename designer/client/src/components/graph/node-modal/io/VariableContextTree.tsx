import { CloudOff } from "@mui/icons-material";
import { alpha, Box, Fade, Stack, Typography } from "@mui/material";
import React, { memo, useCallback, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";

import type { Context } from "../../../../common/TestResultUtils";
import { ContextAccordion } from "./ContextAccordion";
import { ContextTitle } from "./ContextTitle";
import { ContextTree } from "./ContextTree";
import { CountsForNodes } from "./CountsForNodes";
import { useInputOutputContext } from "./InputOutputContext";

type ValuesContextTreeProps = {
    direction?: "input" | "output";
    onIsEmptyChange?: (value: boolean) => void;
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
        inputNodesIds,
        outputNodesIds,
    } = useInputOutputContext();

    const value = useMemo(() => (direction === "input" ? inputDataSetId : outputDataSetId), [direction, inputDataSetId, outputDataSetId]);
    const [availableContexts, hiddenAvailableContexts] = useMemo(() => {
        const contexts = getAvailableContexts(direction);
        const pageSize = 30;
        if (contexts.length > pageSize) {
            const sliced = contexts
                // .sort((a, b) => (b.disabled ? 0 : 1) - (a.disabled ? 0 : 1))
                .filter((r) => !r.disabled)
                .slice(0, pageSize);
            return [sliced, contexts.length - sliced.length];
        }
        return [contexts, 0];
    }, [direction, getAvailableContexts]);

    const setContext = useCallback(
        (context: VariableContextType) =>
            dispatch({
                type: direction === "input" ? "selectInputContext" : "selectOutputContext",
                context,
            }),
        [direction, dispatch],
    );

    const transitionNodesIds = direction === "input" ? inputNodesIds : outputNodesIds;

    return {
        value,
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
    const { value, availableContexts, hiddenAvailableContexts, setContext, inputVariables, transitionNodesIds } =
        useVariableContext(direction);

    useEffect(() => {
        const enabled = availableContexts.filter(({ disabled }) => !disabled);
        if (enabled.length <= 0) return;
        if (value === null) return;
        if (enabled.find(({ id }) => id === value)) return;
        setContext(enabled[0]);
    }, [availableContexts, direction, setContext, value]);

    useEffect(() => {
        onIsEmptyChange?.(transitionNodesIds.length < 1);
    }, [onIsEmptyChange, transitionNodesIds.length]);

    const { t } = useTranslation();

    return (
        <Box
            sx={{
                width: "100%",
                height: "100%",
                minWidth: 260,
            }}
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
                    nodes={transitionNodesIds.map(({ id, results }) => ({
                        id,
                        count: results?.length,
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
                        title={
                            <ContextTitle
                                reversed={direction === "input"}
                                context={r}
                                showNodes={transitionNodesIds.filter((t) => t.id).length > 1}
                            />
                        }
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
                    {t("variableContext.truncatedList", "...and {{count}} more", { count: hiddenAvailableContexts })}
                </Typography>
            ) : null}
        </Box>
    );
});
