import { ClickAwayListener, Tooltip } from "@mui/material";
import type { TooltipProps } from "@mui/material/Tooltip/Tooltip";
import type { PropsWithChildren } from "react";
import React from "react";

import { StyledInfoChildrenWrapper } from "./StyledInfo";
import { StyledInfoMarkdown } from "./StyledInfoMarkdown";
import { useTooltip } from "./useTooltip";

interface Props {
    title: string;
    className?: string;
    customComponentsProps?: TooltipProps["componentsProps"];
    enterDelay?: number;
}

export const InfoTooltipClick = ({ title, className, customComponentsProps, enterDelay, children }: PropsWithChildren<Props>) => {
    const { tooltipRef, tooltipOpen, componentsProps, handleSetTooltipClose, handleToggleTooltip } = useTooltip({
        customComponentsProps,
        enterDelay,
    });

    const handleIconClick = (e) => {
        handleToggleTooltip(e);
    };

    const handleClickAway = (event: MouseEvent) => {
        // Don't close the tooltip if clicking inside the tooltip content
        if (tooltipRef.current && tooltipRef.current.contains(event.target as Node)) {
            return;
        }
        handleSetTooltipClose();
    };

    return (
        <ClickAwayListener onClickAway={handleClickAway}>
            <Tooltip
                title={
                    <div ref={tooltipRef}>
                        <StyledInfoMarkdown>{title}</StyledInfoMarkdown>
                    </div>
                }
                placement={"bottom-start"}
                arrow
                open={tooltipOpen}
                onClose={handleSetTooltipClose}
                disableFocusListener
                disableHoverListener
                disableTouchListener
                componentsProps={componentsProps}
                className={className}
            >
                <StyledInfoChildrenWrapper onClick={handleIconClick}>{children}</StyledInfoChildrenWrapper>
            </Tooltip>
        </ClickAwayListener>
    );
};
