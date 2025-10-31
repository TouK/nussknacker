import { Box, Fade, Typography } from "@mui/material";
import type { MouseEvent, PropsWithChildren } from "react";
import React, { memo, useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { TransitionGroup } from "react-transition-group";
import { useInViewRef } from "rooks";

import { Initiator, startLiveData, stopLiveData } from "../../../../actions/nk/liveData";
import type { ResultContextJson } from "../../../../http/resultsWithCountsDto";
import { getIsLiveDataWorking } from "../../../../reducers/selectors/getLiveData";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { ContextAccordion } from "./ContextAccordion";
import { ContextTree } from "./ContextTree";
import { DoubleSlide } from "./DoubleSlide";
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

const Data = memo(function Data({
    data,
    direction,
    selectedContextCache,
    setContext,
    availableContexts,
    showNodes,
    inputVariables,
    children,
}: PropsWithChildren<{
    data: VariableContextType[];
    direction: "input" | "output";
    selectedContextCache: ResultContextJson & { nodeIds: string[]; error?: string; disabled?: boolean };
    setContext: (context: VariableContextType) => void;
    availableContexts: VariableContextType[];
    showNodes: boolean;
    inputVariables: string[];
}>) {
    const isLiveDataWorking = useAppSelector(getIsLiveDataWorking);
    const [enterVisible, setEnterVisible] = useState(true);
    const [exitVisible, setExitVisible] = useState(false);
    const [ref] = useInViewRef(([{ target, boundingClientRect }]) => {
        const parent = target.parentElement.offsetParent.getBoundingClientRect();

        setEnterVisible(boundingClientRect.top - parent.top + 300 > 0);
        setExitVisible(parent.bottom - boundingClientRect.bottom + 100 > 0);
    });

    const onEntering = useCallback((node: HTMLElement, isAppearing: boolean) => {
        node.classList.add("highlight");
    }, []);

    return (
        <Box sx={{ position: "relative", zIndex: 1 }} ref={(i: HTMLElement) => ref(i)}>
            {children}
            <TransitionGroup enter={enterVisible} exit={exitVisible} appear={false}>
                {data.map((r, index) => (
                    <DoubleSlide key={r.id + r.timestamp} timeout={400} mountOnEnter unmountOnExit onEntering={onEntering}>
                        <ContextAccordion
                            key={r.id + r.timestamp}
                            value={r}
                            direction={direction}
                            disabled={r.disabled}
                            expanded={selectedContextCache?.id === r.id && !r.disabled}
                            onToggle={setContext}
                            locked={index >= availableContexts.length}
                            showNodes={showNodes}
                        >
                            {direction === "output" ? <>{r.error}</> : null}
                            <ContextTree context={r} oldFields={direction === "output" ? inputVariables : []} />
                        </ContextAccordion>
                    </DoubleSlide>
                ))}
            </TransitionGroup>
        </Box>
    );
});

export const VariableContextTree = memo(function ValuesContextTree({
    onIsEmptyChange,
    direction = "input",
}: ValuesContextTreeProps): JSX.Element {
    const { availableContexts, hiddenAvailableContexts, setContext, inputVariables, transitionNodesIds, selectedContextCache } =
        useVariableContext(direction);

    const { t } = useTranslation();
    const dispatch = useAppDispatch();

    useEffect(() => {
        const isEmpty = transitionNodesIds.length < 1;
        if (isEmpty) {
            dispatch(startLiveData(direction === "input" ? Initiator.inputAccordion : Initiator.outputAccordion));
        }
        onIsEmptyChange?.(isEmpty);
    }, [direction, dispatch, onIsEmptyChange, transitionNodesIds.length]);

    const showNodes = transitionNodesIds.length > 1;
    const toggleRefresh = useCallback(
        (e: MouseEvent) => {
            const isEmpty = transitionNodesIds.flatMap(({ results = [] }) => results).length < 1;
            if (!isEmpty) {
                return dispatch(e.type === "mouseenter" ? stopLiveData(Initiator.list) : startLiveData(Initiator.list));
            }
        },
        [dispatch, transitionNodesIds],
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
            <Data
                data={data}
                direction={direction}
                selectedContextCache={selectedContextCache}
                setContext={setContext}
                availableContexts={availableContexts}
                showNodes={showNodes}
                inputVariables={inputVariables}
            >
                <LiveDataLoadingIndicator noLabel={direction === "input"} />
            </Data>
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
