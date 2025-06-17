import type { Theme } from "@mui/material";
import type { TooltipProps } from "@mui/material/Tooltip/Tooltip";
import type { SystemStyleObject } from "@mui/system";
import type { MouseEvent } from "react";
import { useState, useRef, useMemo, useCallback, useEffect } from "react";

import { getBorderColor } from "../../../../../containers/theme/helpers";

export const useTooltip = ({
    customComponentsProps,
    enterDelay = 0,
}: {
    customComponentsProps: TooltipProps["componentsProps"];
    enterDelay?: number;
}) => {
    const [tooltipOpen, setTooltipOpen] = useState(false);
    const tooltipRef = useRef<HTMLDivElement>(null);
    const timerRef = useRef<NodeJS.Timeout | null>(null);

    const componentsProps: TooltipProps["componentsProps"] = useMemo(() => {
        const customTooltipSx: unknown = customComponentsProps?.tooltip?.sx;
        const customArrowSx: unknown = customComponentsProps?.arrow?.sx;

        const defaultTooltipSx = (theme: Theme): SystemStyleObject<Theme> => ({
            fontSize: "0.75rem",
            backgroundColor: theme.palette.background.paper,
            outline: `1px solid ${getBorderColor(theme)}`,
        });
        const defaultArrowSx = (theme: Theme): SystemStyleObject<Theme> => ({
            color: getBorderColor(theme),
        });

        const mergedTooltipSx = (theme: Theme): SystemStyleObject<Theme> => ({
            ...defaultTooltipSx(theme),
            ...(typeof customTooltipSx === "function" ? customTooltipSx(theme) : customTooltipSx || {}),
        });
        const mergedArrowSx = (theme: Theme): SystemStyleObject<Theme> => ({
            ...defaultArrowSx(theme),
            ...(typeof customArrowSx === "function" ? customArrowSx(theme) : customArrowSx || {}),
        });

        return {
            ...customComponentsProps,
            tooltip: {
                ...customComponentsProps?.tooltip,
                sx: mergedTooltipSx,
            },
            arrow: {
                ...customComponentsProps?.arrow,
                sx: mergedArrowSx,
            },
        };
    }, [customComponentsProps]);

    useEffect(() => {
        return () => {
            if (timerRef.current) {
                clearTimeout(timerRef.current);
            }
        };
    }, []);

    const handleSetTooltipOpen = useCallback(() => {
        // Clear any existing timer
        if (timerRef.current) {
            clearTimeout(timerRef.current);
        }

        // Set timer for delayed opening
        timerRef.current = setTimeout(() => {
            setTooltipOpen(true);
            timerRef.current = null;
        }, enterDelay);
    }, [enterDelay]);

    const handleSetTooltipClose = useCallback(() => {
        // Clear timer if tooltip hasn't opened yet
        if (timerRef.current) {
            clearTimeout(timerRef.current);
            timerRef.current = null;
        }
        setTooltipOpen(false);
    }, []);

    const handleToggleTooltip = useCallback((e: MouseEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();
        setTooltipOpen((prev) => !prev);
    }, []);

    return { tooltipOpen, tooltipRef, componentsProps, handleSetTooltipOpen, handleSetTooltipClose, handleToggleTooltip };
};
