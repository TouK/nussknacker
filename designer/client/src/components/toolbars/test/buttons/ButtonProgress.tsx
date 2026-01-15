import type { LinearProgressProps } from "@mui/material";
import { Fade, LinearProgress } from "@mui/material";
import React, { useContext } from "react";

import { ButtonsVariant, ToolbarButtonsContext } from "../../../toolbarComponents/toolbarButtons/ToolbarButtons";

export type ButtonProgressProps = {
    enabled?: boolean;
    value?: LinearProgressProps["value"];
    variant?: LinearProgressProps["variant"];
};

export function ButtonProgress({ enabled, value, variant }: ButtonProgressProps) {
    const btnCtx = useContext(ToolbarButtonsContext);
    const resolvedVariant = variant ?? (value != null && value >= 0 ? "determinate" : "indeterminate");

    return (
        <Fade in={enabled}>
            <LinearProgress
                variant={resolvedVariant}
                value={resolvedVariant === "determinate" ? value ?? 0 : undefined}
                sx={{
                    position: "absolute",
                    inset: 0,
                    height: "auto",
                    pointerEvents: "none",
                    margin: btnCtx.variant === ButtonsVariant.small ? 0 : "4px",
                    background: "transparent",
                    mixBlendMode: "plus-lighter",
                    borderRadius: "6px",
                    "> span": {
                        opacity: 0.25,
                    },
                }}
            />
        </Fade>
    );
}
