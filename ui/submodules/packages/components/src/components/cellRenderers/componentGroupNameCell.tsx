import { useFilterContext } from "../filters/filtersContext";
import { CellLink } from "./cellLink";
import React from "react";
import { GridRenderCellParams } from "@mui/x-data-grid";

export function ComponentGroupNameCell(props: GridRenderCellParams): JSX.Element {
    const { getFilter, setFilter } = useFilterContext();
    const value = getFilter("GROUP", true);
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
