import { Chip } from "@mui/material";
import { GridRenderCellParams } from "@mui/x-data-grid";
import React, { useCallback, useMemo } from "react";
import { useFilterContext } from "../filters/filtersContext";
import { TruncateWrapper } from "./truncateWrapper";

export function CategoryChip({ value }: { value: string }): JSX.Element {
    const { setFilter, getFilter } = useFilterContext();
    const filterValue = useMemo(() => getFilter("CATEGORY", true), [getFilter]);
    const isSelected = useMemo(() => filterValue.includes(value), [filterValue, value]);

    const onClick = useCallback(
        (e) => {
            setFilter("CATEGORY", isSelected ? filterValue.filter((v) => v !== value) : [...filterValue, value]);
            e.preventDefault();
        },
        [filterValue, isSelected, value, setFilter],
    );

    return <Chip tabIndex={0} label={value} size="small" color={isSelected ? "primary" : "default"} onClick={onClick} />;
}

export function CategoriesCell(props: GridRenderCellParams): JSX.Element {
    const { value } = props;
    const elements = value.map((name) => {
        return <CategoryChip key={name} value={name} />;
    });
    return <TruncateWrapper {...props}>{elements}</TruncateWrapper>;
}
