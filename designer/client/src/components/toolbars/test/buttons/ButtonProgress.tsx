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
    return (
        <Fade in={enabled}>
            <LinearProgress
                variant={variant ?? (value >= 0 ? "determinate" : "indeterminate")}
                value={value}
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
