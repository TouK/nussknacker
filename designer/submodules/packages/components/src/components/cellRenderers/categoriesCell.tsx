import React, { useMemo } from "react";
import { TruncateWrapper } from "../../common/utils";
import { CategoryChip, useFilterContext } from "../../common";
import { ComponentsFiltersModel } from "../filters";
import { CellRendererParams } from "../tableWrapper";

export function CategoriesCell(props: CellRendererParams): JSX.Element {
    const { value } = props;
    const { setFilter, getFilter } = useFilterContext<ComponentsFiltersModel>();
    const filterValue = useMemo(() => getFilter("CATEGORY", true), [getFilter]);

    return (
        <TruncateWrapper {...props}>
            {value.map((name) => (
                <CategoryChip key={name} value={name} filterValue={filterValue} setFilter={setFilter("CATEGORY")} />
            ))}
        </TruncateWrapper>
    );
}
