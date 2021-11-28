import React from "react";
import { Box, Chip } from "@mui/material";
import { GridRenderCellParams } from "@mui/x-data-grid";

export function CategoriesCell({ row, filterValue }: GridRenderCellParams & { filterValue: string[] }): JSX.Element {
    return (
        <Box
            sx={{
                flex: 1,
                overflow: "hidden",
                display: "flex",
                gap: 0.5,
                position: "relative",
                maskImage: "linear-gradient(to right, rgba(0, 0, 0, 1) 90%, rgba(0, 0, 0, 0))",
            }}
        >
            {row.categories.map((name) => {
                const isSelected = filterValue.includes(name);
                return (
                    <Chip
                        key={name}
                        label={name}
                        size="small"
                        variant={isSelected ? "outlined" : "filled"}
                        color={isSelected ? "primary" : "default"}
                    />
                );
            })}
        </Box>
    );
}
