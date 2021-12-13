import { Box, Container, Stack, useMediaQuery, useTheme } from "@mui/material";
import React, { PropsWithChildren } from "react";

export function View({ children, inTab }: PropsWithChildren<{ inTab?: boolean }>): JSX.Element {
    const theme = useTheme();
    const md = useMediaQuery(theme.breakpoints.up("md"));

    return (
        <Box
            sx={{
                display: "flex",
                flexDirection: "row",
                minHeight: inTab ? "100%" : "100vh",
                maxHeight: inTab ? "100%" : "unset",
                minWidth: inTab && "100%",
                maxWidth: inTab && "100%",
            }}
        >
            <Container maxWidth="xl" disableGutters={!md}>
                <Stack direction="column" justifyContent="center" height="100%" spacing={2} overflow="hidden" p={md ? 2 : 0.5}>
                    {children}
                </Stack>
            </Container>
        </Box>
    );
}
