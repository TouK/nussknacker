import { CellRendererParams } from "../tableWrapper";
import { useFilterContext } from "../../common";
import React, { useMemo } from "react";
import { CellLink } from "./cellLink";

type Props<M extends Record<string, any>> = CellRendererParams & { filterKey: keyof M; value?: M[keyof M] };

export function FilterLinkCell<M>({ filterKey, ...props }: Props<M>): JSX.Element {
    const { getFilter, setFilter } = useFilterContext<M>();
    const value = useMemo(() => getFilter(filterKey, true), [filterKey, getFilter]);
    const isSelected = value.length === 1 && value.includes(props.value);
    return (
        <CellLink
            disabled={!props.value}
            component={isSelected ? "span" : "button"}
            color={isSelected ? "action.disabled" : "inherit"}
            underline="none"
            onClick={() => setFilter(filterKey, props.value)}
        >
            {props.formattedValue || props.value}
        </CellLink>
    );
}
