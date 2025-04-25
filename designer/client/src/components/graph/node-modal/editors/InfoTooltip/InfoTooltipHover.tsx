import { Tooltip } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

import { StyledInfoChildrenWrapper, StyledInfoMarkdown } from "./StyledInfo";
import { useTooltip } from "./useTooltip";

interface Props {
    text: string;
    className?: string;
}

export const InfoTooltipHover = ({ text, className, children }: PropsWithChildren<Props>) => {
    const { tooltipOpen, tooltipRef, componentsProps, handleSetTooltipClose, handleToggleTooltip } = useTooltip();

    return (
        <Tooltip
            title={
                <div ref={tooltipRef}>
                    <StyledInfoMarkdown>{text}</StyledInfoMarkdown>
                </div>
            }
            placement="bottom-start"
            arrow
            open={tooltipOpen}
            onClose={handleSetTooltipClose}
            disableFocusListener
            disableHoverListener
            disableTouchListener
            componentsProps={componentsProps}
            className={className}
        >
            <StyledInfoChildrenWrapper onMouseEnter={handleToggleTooltip} onMouseLeave={handleToggleTooltip}>
                {children}
            </StyledInfoChildrenWrapper>
        </Tooltip>
    );
};
