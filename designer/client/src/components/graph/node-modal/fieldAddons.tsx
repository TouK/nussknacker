import { Box } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

type FieldAddonsProps = PropsWithChildren<{ hasError?: boolean }>;

export const FieldAddons = ({ hasError, children }: FieldAddonsProps) => (
    <Box
        sx={{
            display: "flex",
            justifyContent: "flex-end",
            //TODO: rearrange this once we have a better way to position buttons
            marginTop: hasError ? "-31px" : "-8px",
        }}
    >
        {children}
    </Box>
);
