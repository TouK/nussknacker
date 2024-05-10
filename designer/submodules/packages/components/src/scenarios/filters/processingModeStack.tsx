import React, { ReactNode } from "react";
import { FilterListItem } from "./filterListItem";
import { OptionsStack } from "./optionsStack";
import { Stack } from "@mui/material";

export interface FiltersParams<V extends string = string, T = string> {
    label: string;
    options?: T[];
    value?: V[];
    onChange?: (value: V[], isChecked: boolean) => void;
}

export function ProcessingModeStack(
    props: FiltersParams<string, { name: string; Icon?: string; displayableName?: ReactNode }>,
): JSX.Element {
    const { options = [], value = [], onChange } = props;
    return (
        <OptionsStack {...props}>
            {options.map((option) => {
                const isSelected = value.includes(option.name);
                const onClick = (checked: boolean) =>
                    onChange(checked ? [...value, option.name] : value.filter((v) => v !== option.name), checked);
                return (
                    <FilterListItem
                        key={option.name}
                        checked={isSelected}
                        onChange={onClick}
                        label={
                            <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                                <span>{option.displayableName}</span>
                                <option.Icon />
                            </Stack>
                        }
                    />
                );
            })}
        </OptionsStack>
    );
}
