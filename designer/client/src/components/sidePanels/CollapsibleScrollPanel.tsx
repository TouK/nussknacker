import { styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { forwardRef, useCallback, useState } from "react";

import type { PanelSide } from "../../actions/nk/ui/panelSide";
import { isDynamic, isLeft, isRight } from "../../actions/nk/ui/panelSide";
import { ErrorBoundary, ToolbarErrorFallbackComponent } from "../common/error-boundary";
import { DRAGGABLE_CLASSNAME, DRAGGABLE_LIST_CLASSNAME } from "../toolbarComponents/ToolbarsContainer";
import { TOOLBAR_WRAPPER_CLASSNAME } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { CollapsiblePanel } from "./CollapsiblePanel";
import { ScrollbarsExtended } from "./ScrollbarsExtended";
import { ScrollPanelContent } from "./SidePanelStyled";

export type CollapsibleScrollPanelProps = PropsWithChildren<{
    isExpanded?: boolean;
    className?: string;
    side?: PanelSide;
    onScrollToggle?: (isEnabled: boolean) => void;
    disabled?: boolean;
}>;

const CollapsibleScrollPanel = forwardRef<HTMLDivElement, CollapsibleScrollPanelProps>(function ScrollTogglePanel(props, ref) {
    const { children, side, isExpanded, onScrollToggle, disabled, className } = props;
    const [scrollVisible, setScrollVisible] = useState(true);

    const scrollToggle = useCallback(
        (isEnabled) => {
            setScrollVisible(isEnabled);
            onScrollToggle?.(isEnabled);
        },
        [onScrollToggle],
    );

    return (
        <CollapsiblePanel
            ref={ref}
            side={side}
            data-testid="SidePanel"
            isExpanded={isExpanded}
            className={className}
            scrollVisible={scrollVisible}
            disabled={disabled}
        >
            <ScrollbarsExtended onScrollToggle={scrollToggle} side={side}>
                <ErrorBoundary FallbackComponent={ToolbarErrorFallbackComponent}>
                    <ScrollPanelContent side={side}>{children}</ScrollPanelContent>
                </ErrorBoundary>
            </ScrollbarsExtended>
        </CollapsiblePanel>
    );
});

export const StyledCollapsibleScrollPanel = styled(CollapsibleScrollPanel)(({ theme, side }) => {
    return {
        [`.${DRAGGABLE_LIST_CLASSNAME}`]: {
            alignItems: isLeft(side) ? "flex-start" : "flex-end",
            margin: theme.spacing(-0.125),
            [isLeft(side) ? "marginRight" : "marginLeft"]: 0,
        },
        [`.${DRAGGABLE_CLASSNAME}`]: {
            margin: theme.spacing(0.125),
        },
        [`.${TOOLBAR_WRAPPER_CLASSNAME}`]: {
            borderBottomRightRadius: isRight(side) ? 0 : null,
            borderTopRightRadius: isRight(side) ? 0 : null,
            borderBottomLeftRadius: isLeft(side) ? 0 : null,
            borderTopLeftRadius: isLeft(side) ? 0 : null,
            boxShadow: isDynamic(side) ? "0 0 2px rgba(0,0,0,0.8),0 0 6px rgba(0,0,0,0.8)" : null,
        },
    };
});
