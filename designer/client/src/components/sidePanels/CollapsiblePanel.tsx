import { styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { forwardRef, useState } from "react";

import { PanelSide } from "../../actions/nk";
import { PANEL_WIDTH, SCROLL_THUMB_SIZE, SIDEBAR_WIDTH } from "../../stylesheets/variables";
import { useGraphViewportAdjustment } from "./graphViewportAdjustment";

type CollapsiblePanelProps = PropsWithChildren<{
    isExpanded?: boolean;
    side: PanelSide;
    className?: string;
    scrollVisible?: boolean;
}>;

const CollapsiblePanelRoot = styled("div")<CollapsiblePanelProps>(({ side, theme }) => ({
    pointerEvents: "none",
    userSelect: "none",
    width: PANEL_WIDTH,
    position: "relative",
    overflow: "hidden",
    willChange: "width",
    transition: theme.transitions.create("width", {
        easing: theme.transitions.easing.easeOut,
        duration: theme.transitions.duration.enteringScreen,
    }),
}));

const CollapsiblePanelContent = styled("div")<CollapsiblePanelProps>(({ side, isExpanded, theme }) => ({
    width: PANEL_WIDTH,
    position: "absolute",
    top: 0,
    bottom: 0,
    overflow: "hidden",
    transition: theme.transitions.create(["left", "right"], {
        easing: theme.transitions.easing.easeOut,
        duration: theme.transitions.duration.enteringScreen,
    }),
}));

const isDynamic = (side: PanelSide) => {
    return [PanelSide.RightDynamic, PanelSide.LeftDynamic].includes(side);
};

export const CollapsiblePanel = forwardRef<HTMLDivElement, CollapsiblePanelProps>(function CollapsiblePanel(
    { children, className, scrollVisible, ...props },
    forwardedRef,
) {
    const [visible, setVisible] = useState(props.isExpanded);
    const width = props.isExpanded ? (scrollVisible || isDynamic(props.side) ? PANEL_WIDTH : SIDEBAR_WIDTH) : 0;

    const [ref] = useGraphViewportAdjustment({
        side: props.side === PanelSide.Left ? "left" : props.side === PanelSide.Right ? "right" : null,
        width,
        forwardedRef,
    });

    return (
        <CollapsiblePanelRoot
            style={{
                width,
                visibility: visible || props.isExpanded ? "visible" : "hidden",
            }}
            ref={ref}
            className={className}
            {...props}
            onTransitionEnd={() => setVisible(props.isExpanded)}
        >
            <CollapsiblePanelContent
                style={{
                    [[PanelSide.Left, PanelSide.LeftDynamic].includes(props.side) ? "right" : "left"]:
                        scrollVisible || isDynamic(props.side) ? 0 : -1 * SCROLL_THUMB_SIZE,
                }}
                {...props}
            >
                {children}
            </CollapsiblePanelContent>
        </CollapsiblePanelRoot>
    );
});
