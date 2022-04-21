import { CellLink } from "./cellLink";
import React, { useMemo } from "react";
import { ComponentsFiltersModel } from "../filters";
import { CellRendererParams } from "../tableWrapper";

export function ComponentGroupNameCell({ filtersContext, ...props }: CellRendererParams<ComponentsFiltersModel>): JSX.Element {
    const { getFilter, setFilter } = filtersContext;
    const value = useMemo(() => getFilter("GROUP", true), [getFilter]);
    const isSelected = value.length === 1 && value.includes(props.value);
    return (
        <CellLink
            disabled={!props.value}
            component={isSelected ? "span" : "button"}
            color={isSelected ? "action.disabled" : "inherit"}
            underline="none"
            onClick={() => setFilter("GROUP", [props.value])}
            cellProps={props}
        >
            {props.value}
        </CellLink>
    );
}
