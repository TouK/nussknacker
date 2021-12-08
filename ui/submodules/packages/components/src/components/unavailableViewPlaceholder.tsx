import { Link, Typography } from "@mui/material";
import React from "react";
import { Link as RouterLink } from "react-router-dom";
import { useHistory } from "../common";

export function UnavailableViewPlaceholder(): JSX.Element {
    const history = useHistory();
    const to = history[history.length - 2] || history[0] || `/`;
    return (
        <Typography variant="h2">
            {`not yet... `}
            <Link component={RouterLink} to={to} replace color="secondary">
                go back!
            </Link>
        </Typography>
    );
}
