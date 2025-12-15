import { Box, darken, styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { memo, useCallback } from "react";
import { ErrorBoundary } from "react-error-boundary";

import { getScrollStyle } from "../node/StyledHeader";
import type { Side, SideState } from "./InputOutputLayout";
import { VariableContextTree } from "./VariableContextTree";

export const SidePane = memo(function SidePane({
    sideState,
    onIsEmptyChange,
    children,
}: PropsWithChildren<{
    sideState: SideState;
    onIsEmptyChange: (side: Side, isEmpty: boolean) => void;
}>) {
    const isEmptyChange = useCallback((isEmpty: boolean) => onIsEmptyChange(sideState.side, isEmpty), [onIsEmptyChange, sideState.side]);
    return (
        <>
            <SidePanelBox
                sx={{
                    alignItems: sideState.side === "left" ? "flex-start" : "flex-end",
                    overflowY: sideState.collapsed ? "hidden" : "auto",
                }}
            >
                <ErrorBoundary fallback={<div>{`ERROR`}</div>}>
                    <VariableContextTree
                        invisible={sideState.collapsed}
                        direction={sideState.side === "left" ? "input" : "output"}
                        onIsEmptyChange={isEmptyChange}
                    />
                </ErrorBoundary>
            </SidePanelBox>
            {children}
        </>
    );
});

const SidePanelBox = styled(Box)(({ theme }) => ({
    "--sidePanelBackground": darken(theme.palette.background.paper, 0.2),
    height: "100%",
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    background: "var(--sidePanelBackground)",
    overflowY: "auto",
    overflowX: "hidden",
    ...getScrollStyle(theme),
}));
