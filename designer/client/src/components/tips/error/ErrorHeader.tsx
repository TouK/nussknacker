import React from "react";
import { Typography } from "@mui/material";

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
