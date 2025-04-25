import { ClickAwayListener, Tooltip } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

import { StyledInfoChildrenWrapper, StyledInfoMarkdown } from "./StyledInfo";
import { useTooltip } from "./useTooltip";

interface Props {
    text: string;
    className?: string;
}

export const InfoTooltipClick = ({ text, className, children }: PropsWithChildren<Props>) => {
    const { tooltipRef, tooltipOpen, componentsProps, handleSetTooltipClose, handleToggleTooltip } = useTooltip();

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
                        <StyledInfoMarkdown>{text}</StyledInfoMarkdown>
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
