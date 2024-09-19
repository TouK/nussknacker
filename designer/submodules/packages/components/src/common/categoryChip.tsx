import React, { useCallback, useMemo } from "react";
import { Button, Chip, Typography } from "@mui/material";

interface Props {
    category: string;
    filterValues: string[];
    setFilter: (value: string[]) => void;
}

export function CategoryChip({ category, filterValues, setFilter }: Props): JSX.Element {
    const isSelected = useMemo(() => filterValues.includes(category), [filterValues, category]);

    const onClick = useCallback(
        (e) => {
            setFilter(isSelected ? filterValues.filter((v) => v !== category) : [...filterValues, category]);
            e.preventDefault();
            e.stopPropagation();
        },
        [setFilter, isSelected, filterValues, category],
    );

    return <Chip tabIndex={0} label={category} size="small" color={isSelected ? "primary" : "default"} onClick={onClick} />;
}

export function CategoryButton({ category, filterValues, setFilter }: Props): JSX.Element {
    const isSelected = useMemo(() => filterValues.includes(category), [filterValues, category]);

    const onClick = useCallback(
        (e) => {
            setFilter(isSelected ? filterValues.filter((v) => v !== category) : [...filterValues, category]);
            e.preventDefault();
            e.stopPropagation();
        },
        [setFilter, isSelected, filterValues, category],
    );

    return (
        <Button
            color={isSelected ? "primary" : "inherit"}
            sx={{
                textTransform: "capitalize",
                display: "flex",
                gap: 1,
                alignItems: "center",
                fontSize: "1rem",
                minWidth: "unset",
                p: 0,
                mx: 0,
            }}
            onClick={onClick}
            aria-selected={isSelected}
        >
            <Typography>{category}</Typography>
        </Button>
    );
}
