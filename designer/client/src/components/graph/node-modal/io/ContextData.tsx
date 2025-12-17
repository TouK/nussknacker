import { Box, Slide, useTheme } from "@mui/material";
import { blend } from "@mui/system";
import React, { memo, type PropsWithChildren, useCallback } from "react";
import { TransitionGroup } from "react-transition-group";

import { getIsLiveDataWorking } from "../../../../reducers/selectors/getLiveData";
import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../store/storeHelpers";
import { ContextAccordion } from "./ContextAccordion";
import { ContextDataDisplay } from "./ContextDataDisplay";
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
    const theme = useTheme();
    const userSettings = useAppSelector(getUserSettings);
    const isLiveDataWorking = useAppSelector(getIsLiveDataWorking);

    const onEntering = useCallback(
        (node: HTMLElement, isAppearing: boolean) => {
            if (isAppearing) return;
            if (!userSettings["node.inputsAndOutputs.showBlinkAnimations"]) return;
            node.animate(
                [
                    {
                        offset: 0,
                        backgroundColor: blend(theme.palette.success.main, theme.palette.background.paper, 0.75),
                        filter: "brightness(125%) saturate(250%)",
                    },
                    {
                        offset: 0.3,
                        backgroundColor: blend(theme.palette.success.main, theme.palette.background.paper, 0.75),
                        filter: "none",
                    },
                    {
                        offset: 1,
                        backgroundColor: theme.palette.background.paper,
                        filter: "none",
                    },
                ],
                { duration: 3000, easing: "ease-out" },
            );
        },
        [theme.palette.background.paper, theme.palette.success.main, userSettings],
    );
    const onEntered = useCallback((node: HTMLElement, isAppearing: boolean) => {
        if (isAppearing) return;
        node.removeAttribute("style");
    }, []);

    return (
        <Box
            sx={{
                position: "relative",
                zIndex: 1,
                flex: 1,
                "&::before": {
                    content: "''",
                    position: "absolute",
                    inset: 0,
                    background: "linear-gradient(transparent 0%, var(--sidePanelBackground) 2.5em, var(--sidePanelBackground) 100%)",
                },
            }}
        >
            {children}
            <TransitionGroup exit={false} appear={false} enter={isLiveDataWorking} component={null}>
                {data.map((r, index) => (
                    <Slide
                        key={r.id + r.timestamp}
                        timeout={Math.max(0, 600 - index * 200)}
                        direction="down"
                        onEntering={onEntering}
                        onEntered={onEntered}
                    >
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
                    </Slide>
                ))}
            </TransitionGroup>
        </Box>
    );
});
