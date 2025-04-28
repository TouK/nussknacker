import type { Theme } from "@mui/material";
import type { TooltipProps } from "@mui/material/Tooltip/Tooltip";
import type { SystemStyleObject } from "@mui/system";
import { useState, useRef, useMemo, useCallback } from "react";

import { getBorderColor } from "../../../../../containers/theme/helpers";

export const useTooltip = ({ customComponentsProps }: { customComponentsProps: TooltipProps["componentsProps"] }) => {
    const [tooltipOpen, setTooltipOpen] = useState(false);
    const tooltipRef = useRef<HTMLDivElement>(null);

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

    const handleSetTooltipClose = useCallback(() => {
        setTooltipOpen(false);
    }, []);

    const handleToggleTooltip = useCallback((e) => {
        e.preventDefault();
        e.stopPropagation();
        setTooltipOpen((prev) => !prev);
    }, []);

    return { tooltipOpen, tooltipRef, componentsProps, handleSetTooltipClose, handleToggleTooltip };
};
