import React from "react";
import { formatAbsolutely, formatRelatively } from "../../common/DateUtils";
import { Typography } from "@mui/material";

export default function Date({ date }: { date: string }): JSX.Element {
    return (
        <Typography variant={"overline"} component={"span"} title={formatAbsolutely(date)}>
            {formatRelatively(date)}
        </Typography>
    );
}
