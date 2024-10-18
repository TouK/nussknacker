import { Stack } from "@mui/material";
import React, { UIEvent, useCallback } from "react";
import { getTabindexedElements } from "../../editors/expression/AceWrapper";
import { SpelChip } from "./spelChip";

export type LastOf<T extends [...unknown[]]> = T extends [...unknown[], infer L] ? L : never;

type ValuesListProps = {
    values: string[];
    onRemove?: (i: number) => void;
    onEdit?: (i: number) => void;
};

export function ValuesList({ values, onRemove, onEdit }: ValuesListProps) {
    const focusPrevious = useCallback((element: HTMLElement) => {
        const [, prev] = getTabindexedElements(element, element);
        prev.pop()?.focus();
    }, []);

    const onDelete = useCallback(
        (index: number) => {
            return ({ currentTarget }: UIEvent<HTMLElement>) => {
                focusPrevious(currentTarget);
                onRemove?.(index);
            };
        },
        [onRemove, focusPrevious],
    );

    return (
        <Stack direction="row" flexWrap="wrap" gap={0.75} m={0.75} tabIndex={-1}>
            {values.map((value, index) => (
                <SpelChip key={index} label={value} onDelete={onDelete(index)} onClick={onEdit ? () => onEdit(index) : null} />
            ))}
        </Stack>
    );
}
