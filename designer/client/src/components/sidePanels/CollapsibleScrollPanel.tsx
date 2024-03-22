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

export const StyledCollapsibleScrollPanel = styled(CollapsibleScrollPanel)(({ theme, side }) => {
    const TOOLBARS_GAP = theme.spacing(0.375);
    return {
        [`.${DRAGGABLE_LIST_CLASSNAME}`]: {
            alignItems: side === PanelSide.Left ? "flex-start" : "flex-end",
        },
        [`.${DRAGGABLE_CLASSNAME}`]: {
            padding: `calc(${TOOLBARS_GAP} / 2) 0`,

            "&:first-of-type": {
                paddingTop: 0,
            },

            "&:last-of-type": {
                paddingBottom: 0,
            },

            [`.${TOOLBAR_WRAPPER_CLASSNAME}`]: {
                position: "relative",
                boxSizing: "border-box",
                overflow: "hidden",
                borderBottomRightRadius: side === PanelSide.Left ? 0 : TOOLBARS_GAP,
                borderTopRightRadius: side === PanelSide.Left ? 0 : TOOLBARS_GAP,
                borderBottomLeftRadius: side === PanelSide.Left ? TOOLBARS_GAP : 0,
                borderTopLeftRadius: side === PanelSide.Left ? TOOLBARS_GAP : 0,
            },
        },
    };
});
