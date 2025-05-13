import { Tooltip } from "@mui/material";
import type { TooltipProps } from "@mui/material/Tooltip/Tooltip";
import type { PropsWithChildren } from "react";
import React from "react";

import { StyledInfoChildrenWrapper, StyledInfoMarkdown } from "./StyledInfo";
import { useTooltip } from "./useTooltip";

interface Props {
    title: string;
    className?: string;
    customComponentsProps?: TooltipProps["componentsProps"];
}

export const InfoTooltipHover = ({ title, className, children, customComponentsProps }: PropsWithChildren<Props>) => {
    const { tooltipOpen, tooltipRef, componentsProps, handleSetTooltipOpen, handleSetTooltipClose } = useTooltip({ customComponentsProps });

    return (
        <Tooltip
            title={
                <div ref={tooltipRef}>
                    <StyledInfoMarkdown>{title}</StyledInfoMarkdown>
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
            TransitionProps={{ timeout: 300 }}
        >
            <StyledInfoChildrenWrapper onMouseEnter={handleSetTooltipOpen} onMouseLeave={handleSetTooltipClose}>
                {children}
            </StyledInfoChildrenWrapper>
        </Tooltip>
    );
};
