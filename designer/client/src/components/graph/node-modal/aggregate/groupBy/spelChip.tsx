import type { ChipProps } from "@mui/material";
import { Chip } from "@mui/material";
import React from "react";

import { HighlightedSpel } from "./highlightedSpel";

export const SpelChip = ({ label, ...props }: ChipProps) => (
    <Chip
        label={<HighlightedSpel>{label}</HighlightedSpel>}
        sx={{
            "& .ace-nussknacker": {
                outline: 0,
                background: "transparent",
            },
        }}
        {...props}
    />
);
