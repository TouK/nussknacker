import { Typography } from "@mui/material";
import React from "react";

import { formatAbsolutely, formatRelatively } from "../../common/DateUtils";

export default function Date({ date }: { date: string }): React.JSX.Element {
    return (
        <Typography variant={"overline"} component={"span"} title={formatAbsolutely(date)}>
            {formatRelatively(date)}
        </Typography>
    );
}
