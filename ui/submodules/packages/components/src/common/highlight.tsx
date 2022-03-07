import { Box } from "@mui/material";
import React, { PropsWithChildren } from "react";

export function Highlight({ children }: PropsWithChildren<unknown>): JSX.Element {
    return (
        <Box component="strong" sx={{ color: "primary.main" }}>
            {children}
        </Box>
    );
}
