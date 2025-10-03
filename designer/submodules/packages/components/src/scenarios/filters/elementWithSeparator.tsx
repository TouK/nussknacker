import { Box, Divider } from "@mui/material";
import React, { forwardRef, type PropsWithChildren } from "react";

export const ElementWithSeparator = forwardRef<HTMLElement, PropsWithChildren<{ showDivider?: boolean }>>(function ElementWithSeparator(
    { children, showDivider = true },
    ref,
) {
    return (
        <Box sx={{ display: "flex" }}>
            <Box ref={ref} sx={{ paddingX: 0.75, paddingY: 0.5, whiteSpace: "nowrap" }}>
                {children}
            </Box>
            {showDivider ? <Divider orientation="vertical" flexItem /> : null}
        </Box>
    );
});
