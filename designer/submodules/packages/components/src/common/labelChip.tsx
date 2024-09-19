import React, { useCallback, useMemo } from "react";
import { Chip } from "@mui/material";

interface Props {
    key: string;
    value: string;
    filterValue: string[];
    setFilter: (value: string[]) => void;
}

export function LabelChip({ key, value, filterValue, setFilter }: Props): JSX.Element {
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
            variant={"outlined"}
            key={key}
            tabIndex={0}
            label={value}
            size="small"
            color={isSelected ? "primary" : "default"}
            onClick={onClick}
        />
    );
}
