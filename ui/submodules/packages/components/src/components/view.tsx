import { Box, Container, Stack } from "@mui/material";
import React, { PropsWithChildren } from "react";

export function View({ children, inTab }: PropsWithChildren<{ inTab?: boolean }>): JSX.Element {
    return (
        <Box
            sx={{
                display: "flex",
                flexDirection: "row",
                minHeight: inTab ? "100%" : "100vh",
                maxHeight: inTab ? "100%" : "100vh",
                minWidth: inTab && "100%",
                maxWidth: inTab && "100%",
            }}
        >
            <Container maxWidth="xl">
                <Stack direction="column" justifyContent="center" height="100%" spacing={2} overflow="hidden" p={2}>
                    {children}
                </Stack>
            </Container>
        </Box>
    );
}
