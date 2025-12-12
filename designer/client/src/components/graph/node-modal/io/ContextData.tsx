import { Box } from "@mui/material";
import React, { memo, type PropsWithChildren, useCallback, useState } from "react";
import { TransitionGroup } from "react-transition-group";
import { useInViewRef } from "rooks";

import { ContextAccordion } from "./ContextAccordion";
import { ContextDataDisplay } from "./ContextDataDisplay";
import { DoubleSlide } from "./DoubleSlide";
import type { Direction, VariableContextType } from "./VariableContextTree";

export const ContextData = memo(function Data({
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
    direction: Direction;
    selectedContextCache: string;
    setContext: (context: VariableContextType) => void;
    availableContexts: number;
    showNodes: boolean;
    inputVariables: string[];
}>) {
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
                            expanded={selectedContextCache === r.id && !r.disabled}
                            onToggle={setContext}
                            locked={index >= availableContexts}
                            showNodes={showNodes}
                        >
                            {direction === "output" ? <>{r.error}</> : null}
                            <ContextDataDisplay direction={direction} context={r} inputVariables={inputVariables} />
                        </ContextAccordion>
                    </DoubleSlide>
                ))}
            </TransitionGroup>
        </Box>
    );
});
