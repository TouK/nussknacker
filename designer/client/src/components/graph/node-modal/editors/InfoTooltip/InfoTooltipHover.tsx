import { Tooltip } from "@mui/material";
import React from "react";

import { MarkdownStyled } from "../../MarkdownStyled";
import { StyledInfoIcon } from "./StyledInfoIcon";
import { useTooltip } from "./useTooltip";

interface HoverProps {
    text: string;
}

export const InfoTooltipHover = ({ text }: HoverProps) => {
    const { tooltipOpen, tooltipRef, componentsProps, handleSetTooltipClose, handleToggleTooltip } = useTooltip();

    return (
        <Tooltip
            title={
                <div ref={tooltipRef}>
                    <MarkdownStyled sx={{ fontSize: "0.75rem" }}>{text}</MarkdownStyled>
                </div>
            }
            placement="bottom-start"
            arrow
            open={tooltipOpen}
            onClose={handleSetTooltipClose}
            disableFocusListener
            disableTouchListener
            componentsProps={componentsProps}
        >
            <StyledInfoIcon onMouseEnter={handleToggleTooltip} onMouseLeave={handleToggleTooltip} />
        </Tooltip>
    );
};
