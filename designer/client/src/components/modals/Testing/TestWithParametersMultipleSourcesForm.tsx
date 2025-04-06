import { Box, Typography } from "@mui/material";
import React from "react";

interface TestWithParametersMultipleSourcesFormProps {
    numberOfSources: number;
}

export function TestWithParametersMultipleSourcesForm({ numberOfSources }: TestWithParametersMultipleSourcesFormProps): JSX.Element {
    return (
        <Box
            sx={{ display: "flex", flexWrap: "wrap", justifyContent: "center", gap: 2, width: "100%" }}
            style={{ marginBottom: "12px", marginTop: "12px" }}
        >
            <Typography component="span" variant={"subtitle1"} noWrap={false} align={"center"}>
                {`Test with form is supported only for scenario with single source. Your scenario has ${numberOfSources} sources.`}
            </Typography>
        </Box>
    );
}
