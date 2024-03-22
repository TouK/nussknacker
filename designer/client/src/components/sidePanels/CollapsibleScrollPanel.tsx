import React, { forwardRef, PropsWithChildren } from "react";
import { CollapsiblePanel } from "./CollapsiblePanel";
import { ScrollbarsExtended } from "./ScrollbarsExtended";
import ErrorBoundary from "../common/ErrorBoundary";
import { ScrollPanelContent } from "./SidePanelStyled";
import { PanelSide } from "./SidePanel";
import { styled } from "@mui/material";
import { DRAGGABLE_CLASSNAME, DRAGGABLE_LIST_CLASSNAME } from "../toolbarComponents/ToolbarsContainer";
import { TOOLBAR_WRAPPER_CLASSNAME } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";

export type CollapsibleScrollPanelProps = PropsWithChildren<{
    isCollapsed?: boolean;
    className?: string;
    side?: PanelSide;
    onScrollToggle?: (isEnabled: boolean) => void;
}>;

const CollapsibleScrollPanel = forwardRef<HTMLDivElement, CollapsibleScrollPanelProps>(function ScrollTogglePanel(props, ref) {
    const { children, side, isCollapsed, onScrollToggle, className } = props;
    return (
        <CollapsiblePanel ref={ref} side={side} isCollapsed={isCollapsed} className={className}>
            <ScrollbarsExtended onScrollToggle={onScrollToggle} side={side}>
                <ErrorBoundary>
                    <ScrollPanelContent side={side}>{children}</ScrollPanelContent>
                </ErrorBoundary>
            </ScrollbarsExtended>
        </CollapsiblePanel>
    );
});

export const StyledCollapsibleScrollPanel = styled(CollapsibleScrollPanel)(({ theme, side }) => ({
    [`.${DRAGGABLE_LIST_CLASSNAME}`]: {
        alignItems: side === PanelSide.Left ? "flex-start" : "flex-end",
        margin: theme.spacing(-0.125),
        [side === PanelSide.Left ? "marginRight" : "marginLeft"]: 0,
    },
    [`.${DRAGGABLE_CLASSNAME}`]: {
        margin: theme.spacing(0.125),
    },
    [`.${TOOLBAR_WRAPPER_CLASSNAME}`]: {
        borderBottomRightRadius: side === PanelSide.Right ? 0 : null,
        borderTopRightRadius: side === PanelSide.Right ? 0 : null,
        borderBottomLeftRadius: side === PanelSide.Left ? 0 : null,
        borderTopLeftRadius: side === PanelSide.Left ? 0 : null,
    },
}));
