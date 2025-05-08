import React, { useMemo } from "react";

import { CategoryChip, TruncateWrapper } from "../../common";
import { useComponentsFilterContext } from "../filters/useComponentsFilterContext";
import type { CellRendererParams } from "../tableWrapper";

export function CategoriesCell(props: CellRendererParams): JSX.Element {
    const { value } = props;
    const { setFilter, getFilter } = useComponentsFilterContext();
    const filterValue = useMemo(() => getFilter("CATEGORY", true), [getFilter]);

    return (
        <TruncateWrapper>
            {value.map((name) => (
                <CategoryChip key={name} category={name} filterValues={filterValue} setFilter={setFilter("CATEGORY")} />
            ))}
        </TruncateWrapper>
    );
}
