import { Badge, Typography } from "@mui/material";
import React from "react";

import { InfoTooltip } from "../graph/node-modal/editors/InfoTooltip/InfoTooltip";

interface Props {
    label: string;
    hasError?: boolean;
    errorTooltip?: string;
}

export function LabelWithErrorIndicator({ label, hasError, errorTooltip }: Readonly<Props>) {
    if (!hasError) {
        return <>{label}</>;
    }

    return (
        <span style={{ position: "relative", display: "inline-block", paddingRight: "8px" }}>
            <Typography sx={{ typography: "body2", color: "text.secondary" }}>{label}</Typography>
            <InfoTooltip title={errorTooltip} variant="hover">
                <Badge color="error" variant="dot" overlap="rectangular" sx={{ position: "absolute", top: 0, right: 0 }}>
                    {/* empty anchor for the badge so tooltip triggers only on the dot */}
                    <span style={{ display: "inline-block", width: 0, height: 0 }} />
                </Badge>
            </InfoTooltip>
        </span>
    );
}
