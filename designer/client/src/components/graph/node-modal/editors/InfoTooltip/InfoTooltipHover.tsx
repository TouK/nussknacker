import { Tooltip } from "@mui/material";
import type { TooltipProps } from "@mui/material/Tooltip/Tooltip";
import type { PropsWithChildren } from "react";
import React from "react";

import { StyledInfoChildrenWrapper, StyledInfoMarkdown } from "./StyledInfo";
import { useTooltip } from "./useTooltip";

interface Props {
    text: string;
    className?: string;
    customComponentsProps?: TooltipProps["componentsProps"];
}

export const InfoTooltipHover = ({ text, className, children, customComponentsProps }: PropsWithChildren<Props>) => {
    const { tooltipOpen, tooltipRef, componentsProps, handleSetTooltipClose, handleToggleTooltip } = useTooltip({ customComponentsProps });

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
