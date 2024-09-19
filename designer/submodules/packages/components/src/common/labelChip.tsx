import React, { useCallback, useMemo } from "react";
import { Chip } from "@mui/material";

interface Props {
    id: string;
    value: string;
    filterValue: string[];
    setFilter: (value: string[]) => void;
}

export function LabelChip({ id, value, filterValue, setFilter }: Props): JSX.Element {
    const isSelected = useMemo(() => filterValue.includes(value), [filterValue, value]);

    const onClick = useCallback(
        (e) => {
            setFilter(isSelected ? filterValue.filter((v) => v !== value) : [...filterValue, value]);
            e.preventDefault();
            e.stopPropagation();
        },
        [setFilter, isSelected, filterValue, value],
    );

    return (
        <Chip
            key={id}
            color={isSelected ? "primary" : "default"}
            size="small"
            variant={"outlined"}
            label={value}
            tabIndex={0}
            onClick={onClick}
            sx={{ borderRadius: "16px" }}
        />
    );
}
