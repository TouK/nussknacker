import React, { useCallback, useMemo } from "react";
import { Link } from "@mui/material";
import { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";
import { useFilterContext } from "../../common";

export function Author({ value }: { value: string }): JSX.Element {
    const { setFilter, getFilter } = useFilterContext<ScenariosFiltersModel>();
    const filterValue = useMemo(() => getFilter("CREATED_BY", true), [getFilter]);
    const isSelected = useMemo(() => filterValue.includes(value), [filterValue, value]);

    const onClick = useCallback(
        (e) => {
            setFilter("CREATED_BY", isSelected ? filterValue.filter((val) => val !== value) : [...filterValue, value]);
            e.preventDefault();
            e.stopPropagation();
        },
        [filterValue, isSelected, value, setFilter],
    );

    return (
        <Link
            component="button"
            variant="caption"
            sx={{ color: isSelected ? "primary.main" : "inherit", verticalAlign: "initial" }}
            tabIndex={0}
            onClick={onClick}
        >
            {value}
        </Link>
    );
}
