import type { ChipProps } from "@mui/material";
import { Chip } from "@mui/material";
import React from "react";

import { StaticHighlighted } from "./staticHighlighted";

export const SpelChip = ({ label, ...props }: ChipProps) => (
    <Chip
        size="small"
        variant="outlined"
        label={<StaticHighlighted>{label}</StaticHighlighted>}
        sx={{
            "& .ace-nussknacker": {
                outline: 0,
                background: "transparent",
            },
        }}
        {...props}
    />
);
