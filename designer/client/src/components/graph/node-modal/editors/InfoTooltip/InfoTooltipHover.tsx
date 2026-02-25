import { Box, Tooltip } from "@mui/material";
import type { TooltipProps } from "@mui/material/Tooltip/Tooltip";
import type { PropsWithChildren, ReactNode } from "react";
import React, { useCallback, useEffect, useRef } from "react";

import { StyledInfoChildrenWrapper } from "./StyledInfo";
import { useTooltip } from "./useTooltip";

interface Props {
    title: ReactNode;
    className?: string;
    customComponentsProps?: TooltipProps["componentsProps"];
    enterDelay?: number;
}

export const InfoTooltipHover = ({ title, className, children, customComponentsProps, enterDelay }: PropsWithChildren<Props>) => {
    const { tooltipOpen, tooltipRef, componentsProps, handleSetTooltipOpen, handleSetTooltipClose } = useTooltip({
        customComponentsProps: {
            ...customComponentsProps,
            tooltip: {
                ...customComponentsProps?.tooltip,
                sx: {
                    padding: 0,
                    ...customComponentsProps?.tooltip?.sx,
                },
            },
        },
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
                <Box
                    px={1}
                    py={0.5}
                    ref={tooltipRef}
                    onPointerEnter={() => {
                        clearCloseTimer();
                        handleSetTooltipOpen();
                    }}
                    onPointerLeave={() => {
                        scheduleClose();
                    }}
                >
                    {title}
                </Box>
            }
            placement="right-start"
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
