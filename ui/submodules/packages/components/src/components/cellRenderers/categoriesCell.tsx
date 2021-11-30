import React, { useCallback } from "react";
import { Box, Chip } from "@mui/material";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { useFilterContext } from "../filters/filtersContext";
import { useCellArrowKeys } from "./cellLink";

function CategoryChip({ filterValue, name }: { filterValue: string[]; name: string }): JSX.Element {
    const isSelected = filterValue.includes(name);
    const { getFilter, setFilter } = useFilterContext();

    const onClick = useCallback(() => {
        const category = getFilter("CATEGORY", true);
        setFilter("CATEGORY", isSelected ? category.filter((value) => value !== name) : [...category, name]);
    }, [getFilter, isSelected, name, setFilter]);

    return (
        <Chip
            tabIndex={0}
            label={name}
            size="small"
            variant={isSelected ? "outlined" : "filled"}
            color={isSelected ? "primary" : "default"}
            onClick={onClick}
        />
    );
}

export function CategoriesCell(props: GridRenderCellParams & { filterValue: string[] }): JSX.Element {
    const { row, filterValue } = props;
    const handleCellKeyDown = useCellArrowKeys(props);
    return (
        <Box
            onKeyDown={handleCellKeyDown}
            sx={{
                flex: 1,
                overflow: "hidden",
                display: "flex",
                gap: 0.5,
                position: "relative",
                maskImage: "linear-gradient(to right, rgba(0, 0, 0, 1) 90%, rgba(0, 0, 0, 0))",
            }}
        >
            {row.categories.map((name) => (
                <CategoryChip key={name} filterValue={filterValue} name={name} />
            ))}
        </Box>
    );
}
