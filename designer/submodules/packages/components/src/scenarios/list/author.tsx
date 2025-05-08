import { Link } from "@mui/material";
import React, { useCallback, useMemo } from "react";

import { useScenariosFilterContext } from "../filters/common/useScenariosFilterContext";

export function Author({ value }: { value: string }): JSX.Element {
    const { setFilter, getFilter } = useScenariosFilterContext();
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
