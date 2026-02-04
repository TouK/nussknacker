import { Box, Slide, useTheme } from "@mui/material";
import React, { memo, type PropsWithChildren, useCallback } from "react";
import { TransitionGroup } from "react-transition-group";

import { useUserSettings } from "../../../../common/useUserSettings";
import { getIsLiveDataWorking } from "../../../../reducers/selectors/getLiveData";
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
    const [showBlinkAnimations] = useUserSettings("node.inputsAndOutputs.showBlinkAnimations");
    const isLiveDataWorking = useAppSelector(getIsLiveDataWorking);

    const onEnter = useCallback(
        (node: HTMLElement, isAppearing: boolean) => {
            if (isAppearing) return;
            if (!showBlinkAnimations) return;
            node.animate([{ backgroundColor: theme.palette.success.main }, { backgroundColor: theme.palette.background.paper }], {
                duration: 3000,
                easing: "cubic-bezier(0, 0.55, 0.45, 1)",
            });
        },
        [showBlinkAnimations, theme.palette.background.paper, theme.palette.success.main],
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
                        onEnter={onEnter}
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
