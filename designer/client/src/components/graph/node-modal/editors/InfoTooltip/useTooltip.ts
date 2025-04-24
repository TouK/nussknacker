import type { Theme } from "@mui/material";
import type { TooltipProps } from "@mui/material/Tooltip/Tooltip";
import { useState, useRef, useMemo, useCallback } from "react";

import { getBorderColor } from "../../../../../containers/theme/helpers";

export const useTooltip = () => {
    const [tooltipOpen, setTooltipOpen] = useState(false);
    const tooltipRef = useRef<HTMLDivElement>(null);

    const componentsProps: TooltipProps["componentsProps"] = useMemo(
        () => ({
            tooltip: {
                sx: (theme: Theme) => ({
                    fontSize: "0.75rem",
                    backgroundColor: theme.palette.background.paper,
                    outline: `1px solid ${getBorderColor(theme)}`,
                    maxWidth: "none",
                }),
            },
            arrow: {
                sx: (theme: Theme) => ({
                    color: getBorderColor(theme),
                }),
            },
        }),
        [],
    );

    const handleSetTooltipClose = useCallback(() => {
        setTooltipOpen(false);
    }, []);

    const handleToggleTooltip = useCallback(() => {
        setTooltipOpen((prev) => !prev);
    }, []);

    return { tooltipOpen, tooltipRef, componentsProps, handleSetTooltipClose, handleToggleTooltip };
};
