import { Box, Fade, LinearProgress, LinearProgressProps } from "@mui/material";
import React, { PropsWithChildren, useContext } from "react";
import { ButtonsVariant, ToolbarButtonsContext } from "../../../toolbarComponents/toolbarButtons";

export function ButtonProgress({
    children,
    enabled,
    variant,
    value,
}: PropsWithChildren<
    {
        enabled?: boolean;
    } & Pick<LinearProgressProps, "variant" | "value">
>) {
    const btnCtx = useContext(ToolbarButtonsContext);
    return (
        <Box sx={{ position: "relative" }}>
            {children}
            <Fade in={enabled}>
                <LinearProgress
                    variant={variant}
                    value={value}
                    sx={{
                        position: "absolute",
                        inset: 0,
                        height: "auto",
                        pointerEvents: "none",
                        margin: btnCtx.variant === ButtonsVariant.small ? 0 : "4px",
                        background: "transparent",
                        mixBlendMode: "luminosity",
                        borderRadius: "6px",
                        "> span": {
                            opacity: 0.25,
                        },
                    }}
                />
            </Fade>
        </Box>
    );
}
