import { Chip, Stack } from "@mui/material";
import type { StackProps } from "@mui/material/Stack/Stack";
import type { UIEvent } from "react";
import React, { useCallback } from "react";

import { getTabindexedElements } from "../../editors/expression/AceWrapper";

export type ValuesListProps = {
    values: string[];
    onRemove?: (i: number) => void;
    onEdit?: (i: number) => void;
    getLabel?: (value: string, i: number) => string;
    isValid?: (value: string) => boolean;
    ChipComponent?: typeof Chip;
};

function isElementMatchingSelector(event: React.MouseEvent<unknown>, selectors: string) {
    return event.nativeEvent.composedPath().some((t) => {
        return (t as HTMLElement)?.matches?.(selectors);
    });
}

export function ValuesList({
    values,
    onRemove,
    onEdit,
    ChipComponent = Chip,
    getLabel = (value) => value,
    isValid = () => true,
    ...props
}: ValuesListProps & StackProps) {
    const focusPrevious = useCallback((element: HTMLElement) => {
        const [, prev] = getTabindexedElements(element, element);
        prev.pop()?.focus();
    }, []);

    const onDelete = useCallback(
        (index: number) => {
            if (!onRemove) return undefined;

            return ({ currentTarget }: UIEvent<HTMLElement>) => {
                focusPrevious(currentTarget);
                onRemove(index);
            };
        },
        [onRemove, focusPrevious],
    );

    const onClick = useCallback(
        (index: number) => {
            if (!onEdit) return undefined;

            return () => {
                onEdit(index);
            };
        },
        [onEdit],
    );

    return (
        <Stack
            direction="row"
            flexWrap="wrap"
            gap={0.75}
            m={0.6875}
            tabIndex={-1}
            onPointerDown={(event) => {
                const strings = [];
                if (onEdit) strings.push(".MuiChip-root");
                if (onDelete) strings.push(".MuiChip-deleteIcon");
                if (isElementMatchingSelector(event, strings.join(","))) {
                    event.preventDefault();
                }
            }}
            {...props}
        >
            {values.map((value, index) => {
                return (
                    <ChipComponent
                        key={index}
                        size="small"
                        variant="outlined"
                        color={isValid(value) ? "default" : "error"}
                        label={getLabel(value, index)}
                        onDelete={onDelete(index)}
                        onClick={onClick(index)}
                    />
                );
            })}
        </Stack>
    );
}
