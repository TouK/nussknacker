import { styled } from "@mui/material";
import React, { forwardRef, PropsWithChildren } from "react";
import { PANEL_WIDTH } from "../../stylesheets/variables";
import { PanelSide } from "./SidePanel";

type CollapsiblePanelProps = PropsWithChildren<{
    isCollapsed?: boolean;
    side: PanelSide;
    className?: string;
}>;

const CollapsiblePanelRoot = styled("div")<CollapsiblePanelProps>(({ side }) => ({
    pointerEvents: "none",
    userSelect: "none",
    width: PANEL_WIDTH,
    position: "absolute",
    zIndex: 1,
    top: 0,
    bottom: 0,
    overflow: "hidden",
    [side === PanelSide.Left ? "left" : "right"]: 0,
}));

const CollapsiblePanelContent = styled("div")<CollapsiblePanelProps>(({ side, isCollapsed, theme }) => ({
    width: PANEL_WIDTH,
    transition: theme.transitions.create(["left", "right"], {
        duration: theme.transitions.duration.short,
        easing: theme.transitions.easing.easeInOut,
    }),
    position: "absolute",
    top: 0,
    bottom: 0,
    overflow: "hidden",
    [side === PanelSide.Left ? "left" : "right"]: isCollapsed ? 0 : -PANEL_WIDTH,
}));

export const CollapsiblePanel = forwardRef<HTMLDivElement, CollapsiblePanelProps>(function CollapsiblePanel(
    { children, className, ...props },
    ref,
) {
    return (
        <CollapsiblePanelRoot ref={ref} className={className} {...props}>
            <CollapsiblePanelContent {...props}>{children}</CollapsiblePanelContent>
        </CollapsiblePanelRoot>
    );
});
