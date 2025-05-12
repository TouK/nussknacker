import { Typography } from "@mui/material";
import React from "react";

interface Props {
    message: string;
}
export const ErrorHeader = ({ message }: Props) => {
    return (
        <Typography component={"span"} variant={"body2"}>
            {message}
        </Typography>
    );
};
