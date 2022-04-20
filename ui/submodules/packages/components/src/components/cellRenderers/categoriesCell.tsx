import React, { useMemo } from "react";
import { TruncateWrapper } from "../utils";
import { CategoryChip } from "../../common";
import { ComponentsFiltersModel } from "../filters";
import { CellRendererParams } from "../tableWrapper";

export function CategoriesCell({ filtersContext, ...props }: CellRendererParams<ComponentsFiltersModel>): JSX.Element {
    const { value } = props;
    const { setFilterImmediately, getFilter } = filtersContext;
    const filterValue = useMemo(() => getFilter("CATEGORY", true), [getFilter]);

    return (
        <TruncateWrapper {...props}>
            {value.map((name) => (
                <CategoryChip key={name} value={name} filterValue={filterValue} setFilter={setFilterImmediately("CATEGORY")} />
            ))}
        </TruncateWrapper>
    );
}
