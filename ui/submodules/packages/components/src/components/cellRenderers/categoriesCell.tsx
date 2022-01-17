import { Chip } from "@mui/material";
import { GridRenderCellParams } from "@mui/x-data-grid";
import React, { useCallback } from "react";
import { useFilterContext } from "../filters/filtersContext";
import { TruncateWrapper } from "./truncateWrapper";

function CategoryChip({ filterValue, name }: { filterValue: string[]; name: string }): JSX.Element {
    const isSelected = filterValue.includes(name);
    const { getFilter, setFilter } = useFilterContext();

    const onClick = useCallback(() => {
        const category = getFilter("CATEGORY", true);
        setFilter("CATEGORY", isSelected ? category.filter((value) => value !== name) : [...category, name]);
    }, [getFilter, isSelected, name, setFilter]);

    return <Chip tabIndex={0} label={name} size="small" color={isSelected ? "primary" : "default"} onClick={onClick} />;
}

export function CategoriesCell(props: GridRenderCellParams & { filterValue: string[] }): JSX.Element {
    const { value, filterValue } = props;
    const elements = value.map((name) => {
        return <CategoryChip key={name} filterValue={filterValue} name={name} />;
    });
    return <TruncateWrapper {...props}>{elements}</TruncateWrapper>;
}

