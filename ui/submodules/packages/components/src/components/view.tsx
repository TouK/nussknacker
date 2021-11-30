import { Container, Stack } from "@mui/material";
import React from "react";
import { HistoryProvider } from "../common";
import { ListWithFilters } from "./listWithFilters";
import { BrowserRouter as Router, Route, Routes } from "react-router-dom";
import { UnavailableViewPlaceholder } from "./unavailableViewPlaceholder";

export function View(props: { basepath?: string }): JSX.Element {
    return (
        <Router basename={props.basepath}>
            <HistoryProvider>
                <Container maxWidth="xl">
                    <Stack direction="column" justifyContent="center" height="100%" spacing={2} overflow="hidden" p={2}>
                        <Routes>
                            <Route path="/" element={<ListWithFilters />} />
                            <Route path="*" element={<UnavailableViewPlaceholder />} />
                        </Routes>
                    </Stack>
                </Container>
            </HistoryProvider>
        </Router>
    );
}
