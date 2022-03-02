import { Box, Link, Typography } from "@mui/material";
import React from "react";
import { Link as RouterLink } from "react-router-dom";
import { useBackHref } from "../common";

export function UnavailableViewPlaceholder(): JSX.Element {
    const to = useBackHref();
    return (
        <Box flex={1} display="flex" flexDirection="row" justifyContent="center" alignItems="center">
            <Typography variant="h2" sx={{ marginBottom: ".8em" }}>
                {`not yet... `}
                <Link component={RouterLink} to={to} replace color="secondary">
                    go back!
                </Link>
            </Typography>
        </Box>
    );
}
