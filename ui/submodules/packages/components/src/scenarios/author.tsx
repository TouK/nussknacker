import { useFilterContext } from "../common/filters";
import React, { useCallback, useMemo } from "react";
import { Link } from "@mui/material";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";

export function Author({ value }: { value: string }): JSX.Element {
    const { setFilter, getFilter } = useFilterContext<ScenariosFiltersModel>();
    const filterValue = useMemo(() => getFilter("CREATED_BY", true), [getFilter]);
    const isSelected = useMemo(() => filterValue.includes(value), [filterValue, value]);

    const onClick = useCallback(
        (e) => {
            setFilter("CREATED_BY", isSelected ? filterValue.filter((val) => val !== value) : [...filterValue, value]);
            e.preventDefault();
        },
        [filterValue, isSelected, value, setFilter],
    );

    return (
        <Link component="button" variant="body2" sx={{ color: isSelected ? "primary.main" : "inherit" }} tabIndex={0} onClick={onClick}>
            {value}
        </Link>
    );
}
