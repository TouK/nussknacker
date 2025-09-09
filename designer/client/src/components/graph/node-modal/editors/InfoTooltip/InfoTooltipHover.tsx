import { Tooltip } from "@mui/material";
import type { TooltipProps } from "@mui/material/Tooltip/Tooltip";
import type { PropsWithChildren } from "react";
import React, { useRef, useCallback, useEffect } from "react";

import { StyledInfoChildrenWrapper, StyledInfoMarkdown } from "./StyledInfo";
import { useTooltip } from "./useTooltip";

interface Props {
    title: string;
    className?: string;
    customComponentsProps?: TooltipProps["componentsProps"];
    enterDelay?: number;
}

export const InfoTooltipHover = ({ title, className, children, customComponentsProps, enterDelay }: PropsWithChildren<Props>) => {
    const { tooltipOpen, tooltipRef, componentsProps, handleSetTooltipOpen, handleSetTooltipClose } = useTooltip({
        customComponentsProps,
        enterDelay,
    });

    // Delay closing to allow moving pointer from trigger to tooltip without flicker
    const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const clearCloseTimer = useCallback(() => {
        if (closeTimerRef.current) {
            clearTimeout(closeTimerRef.current);
            closeTimerRef.current = null;
        }
    }, []);
    const scheduleClose = useCallback(() => {
        clearCloseTimer();
        closeTimerRef.current = setTimeout(() => {
            handleSetTooltipClose();
            closeTimerRef.current = null;
        }, 300);
    }, [clearCloseTimer, handleSetTooltipClose]);

    useEffect(() => () => clearCloseTimer(), [clearCloseTimer]);

    return (
        <Tooltip
            title={
                <div
                    ref={tooltipRef}
                    onPointerEnter={() => {
                        clearCloseTimer();
                        handleSetTooltipOpen();
                    }}
                    onPointerLeave={() => {
                        scheduleClose();
                    }}
                >
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
            <StyledInfoChildrenWrapper
                onPointerEnter={(e) => {
                    if (e.pointerType === "mouse") {
                        clearCloseTimer();
                        handleSetTooltipOpen();
                    }
                }}
                onPointerLeave={(e) => {
                    const relatedTarget = e.relatedTarget as Node | null;
                    if (tooltipRef.current && relatedTarget && tooltipRef.current.contains(relatedTarget)) return; // heading into tooltip
                    scheduleClose();
                }}
                onBlur={scheduleClose}
            >
                {children}
            </StyledInfoChildrenWrapper>
        </Tooltip>
    );
};
