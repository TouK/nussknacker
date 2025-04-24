import { ClickAwayListener, Tooltip } from "@mui/material";
import React from "react";

import { MarkdownStyled } from "../../MarkdownStyled";
import { StyledInfoIcon } from "./StyledInfoIcon";
import { useTooltip } from "./useTooltip";

interface Props {
    text: string;
}

export const InfoTooltipClick = ({ text }: Props) => {
    const { tooltipRef, tooltipOpen, componentsProps, handleSetTooltipClose, handleToggleTooltip } = useTooltip();

    const handleIconClick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        handleToggleTooltip();
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
                        <MarkdownStyled sx={{ fontSize: "0.75rem" }}>{text}</MarkdownStyled>
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
            >
                <StyledInfoIcon onClick={handleIconClick} />
            </Tooltip>
        </ClickAwayListener>
    );
};
