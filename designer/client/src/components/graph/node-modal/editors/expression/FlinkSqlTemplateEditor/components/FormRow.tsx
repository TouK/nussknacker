import { Box, Typography } from "@mui/material";
import React from "react";
import type { ReactNode } from "react";

import { InfoTooltip } from "../../../InfoTooltip/InfoTooltip";

// Match Nu's formLabelWidth from styles.ts: labels take 20%, values flex: 1
export const LABEL_FLEX_BASIS = "20%";

interface FormRowProps {
    label: string;
    tooltip?: string;
    children: ReactNode;
    alignItems?: string;
}

export function FormRow({ label, tooltip, children, alignItems = "flex-start" }: FormRowProps) {
    return (
        <Box display="flex" alignItems={alignItems} sx={{ margin: "16px 0" }}>
            <Box
                sx={{
                    flexBasis: LABEL_FLEX_BASIS,
                    flexShrink: 0,
                    pt: 0.75,
                    pr: 1.5,
                    minWidth: 0,
                    display: "flex",
                    alignItems: "center",
                    gap: 0.5,
                }}
            >
                <Typography variant="body2" sx={{ color: "text.secondary", overflowWrap: "anywhere" }}>
                    {label}
                </Typography>
                {tooltip && <InfoTooltip title={tooltip} variant="hover" placement="top-start" />}
            </Box>
            <Box sx={{ flex: 1, minWidth: 0 }}>{children}</Box>
        </Box>
    );
}
