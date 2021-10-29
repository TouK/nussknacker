import { Stack } from "@mui/material";
import Container from "@mui/material/Container";
import React from "react";
import { ScenariosTable } from "./scenarios";

export function Demo(): JSX.Element {
    return (
        <Container maxWidth="xl">
            <Stack direction="column" justifyContent="center" height="100%" spacing={2} overflow="hidden" p={2}>
                <ScenariosTable />
            </Stack>
        </Container>
    );
}
