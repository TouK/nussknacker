import { ExpandLess } from "@mui/icons-material";
import { Box, styled } from "@mui/material";
import { Allotment } from "allotment";
import React, { memo } from "react";
import { ErrorBoundary } from "react-error-boundary";

import { getScrollStyle } from "../node/StyledHeader";
import type { Side, SideState } from "./InputOutputLayout";
import { PanelButton } from "./PanelButton";
import { VariableContextTree } from "./VariableContextTree";

export const SidePane = memo(function SidePane({
    sideState,
    collapsedSize,
    onIsEmptyChange,
    onToggleClick,
}: {
    sideState: SideState;
    collapsedSize: number;
    onIsEmptyChange: (side: Side, isEmpty: boolean) => void;
    onToggleClick: (side: Side) => void;
}) {
    return (
        <Allotment.Pane
            preferredSize="20%"
            minSize={sideState.hidden ? 0 : collapsedSize}
            maxSize={sideState.hidden ? 0 : Infinity}
            visible={!sideState.hidden}
        >
            <SidePanelBox
                sx={{
                    alignItems: sideState.side === "left" ? "flex-start" : "flex-end",
                    overflowY: sideState.collapsed ? "hidden" : "auto",
                }}
            >
                <ErrorBoundary fallback={<div>{`ERROR`}</div>}>
                    <VariableContextTree direction="input" onIsEmptyChange={(isEmpty) => onIsEmptyChange(sideState.side, isEmpty)} />
                </ErrorBoundary>
            </SidePanelBox>
            <PanelButton side={sideState.side} collapsed={sideState.collapsed} onClick={() => onToggleClick(sideState.side)}>
                <ExpandLess />
            </PanelButton>
        </Allotment.Pane>
    );
});

const SidePanelBox = styled(Box)(({ theme }) => ({
    height: "100%",
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    background: "rgba(0,0,0,.2)",
    overflowY: "auto",
    overflowX: "hidden",
    ...getScrollStyle(theme),
}));
