import React, { PropsWithChildren } from "react";
import { Box, LinearProgress } from "@mui/material";

function LinearIndeterminate(): JSX.Element {
    return (
        <Box sx={{ width: "100%" }}>
            <LinearProgress color="secondary" />
        </Box>
    );
}

export function DefaultSuspense({ children }: PropsWithChildren<unknown>): JSX.Element {
    return <React.Suspense fallback={<LinearIndeterminate />}>{children}</React.Suspense>;
}
