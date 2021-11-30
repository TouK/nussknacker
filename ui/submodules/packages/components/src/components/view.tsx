import { Container, Link, Stack, Typography, Paper } from "@mui/material";
import React from "react";
import { ListWithFilters } from "./listWithFilters";
import { BrowserRouter as Router, Link as RouterLink, Route, Routes } from "react-router-dom";

export function View(props: { basepath?: string }): JSX.Element {
    return (
        <Router basename={props.basepath}>
            <Container maxWidth="xl">
                <Stack direction="column" justifyContent="center" height="100%" spacing={2} overflow="hidden" p={2}>
                    <Routes>
                        <Route path="/" element={<ListWithFilters />} />
                        <Route
                            path="/:componentId"
                            element={
                                <Typography variant="h2">
                                    {`not yet... `}
                                    <Link component={RouterLink} to={`/`} replace color="secondary">
                                        go back!
                                    </Link>
                                </Typography>
                            }
                        />
                    </Routes>
                </Stack>
            </Container>
        </Router>
    );
}
