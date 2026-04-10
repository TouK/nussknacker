import { Badge, Typography } from "@mui/material";
import React from "react";

import { InfoTooltip } from "../graph/node-modal/editors/InfoTooltip/InfoTooltip";

interface Props {
    label: string;
    errorCount?: number;
    errorTooltip?: string;
}

export function LabelWithErrorIndicator({ label, errorCount, errorTooltip }: Readonly<Props>) {
    if (!errorCount) {
        return <Typography variant="body2">{label}</Typography>;
    }

    return (
        <InfoTooltip title={errorTooltip} variant="hover">
            <Badge
                color="error"
                variant="standard"
                badgeContent={errorCount}
                sx={{ "& .MuiBadge-badge": { fontSize: 9, minWidth: 14, height: 14, padding: "0 3px", right: -8 } }}
            >
                <Typography variant="body2">{label}</Typography>
            </Badge>
        </InfoTooltip>
    );
}
