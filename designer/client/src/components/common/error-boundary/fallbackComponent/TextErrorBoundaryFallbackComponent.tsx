import React from "react";
import { Alert, Typography } from "@mui/material";

interface Props {
    header: string;
    message: string;
}
export const TextErrorBoundaryFallbackComponent = ({ header, message }: Props) => {
    return (
        <Alert severity="error" sx={{ width: "100%" }}>
            <Typography variant={"subtitle1"}>{header}</Typography>
            <Typography variant={"body2"}>{message}</Typography>
        </Alert>
    );
};
