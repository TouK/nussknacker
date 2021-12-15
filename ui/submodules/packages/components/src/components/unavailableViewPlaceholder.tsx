import { Link, Typography } from "@mui/material";
import React from "react";
import { Link as RouterLink } from "react-router-dom";
import { useBackHref } from "../common";

export function UnavailableViewPlaceholder(): JSX.Element {
    const to = useBackHref();
    return (
        <Typography variant="h2">
            {`not yet... `}
            <Link component={RouterLink} to={to} replace color="secondary">
                go back!
            </Link>
        </Typography>
    );
}
