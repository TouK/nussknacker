import { CellRendererParams } from "../tableWrapper";
import { useFilterContext } from "../../common";
import { UsagesFiltersModel } from "../usages/usagesFiltersModel";
import React, { useMemo } from "react";
import { CellLink } from "./cellLink";

export function ScenarioAuthorCell(props: CellRendererParams): JSX.Element {
    const { getFilter, setFilter } = useFilterContext<UsagesFiltersModel>();
    const value = useMemo(() => getFilter("CREATED_BY", true), [getFilter]);
    const isSelected = value.length === 1 && value.includes(props.value);
    return (
        <CellLink
            disabled={!props.value}
            component={isSelected ? "span" : "button"}
            color={isSelected ? "action.disabled" : "inherit"}
            underline="none"
            onClick={() => setFilter("CREATED_BY", [props.value])}
            cellProps={props}
        >
            {props.value}
        </CellLink>
    );
}
