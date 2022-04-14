import { GridRenderCellParams } from "@mui/x-data-grid";
import React from "react";
import { TruncateWrapper } from "../utils";
import { CategoryChip, useFilterContext } from "../../common";
import { ComponentsFiltersModel } from "../filters";

export function CategoriesCell(props: GridRenderCellParams): JSX.Element {
    const { value } = props;
    const { setFilter, getFilter } = useFilterContext<ComponentsFiltersModel>();
    const filterValue = getFilter("CATEGORY", true);

    return (
        <TruncateWrapper {...props}>
            {value.map((name) => (
                <CategoryChip key={name} value={name} filterValue={filterValue} setFilter={setFilter("CATEGORY")} />
            ))}
        </TruncateWrapper>
    );
}
