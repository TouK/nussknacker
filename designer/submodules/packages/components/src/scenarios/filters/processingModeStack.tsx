import React from "react";
import { FilterListItem } from "./filterListItem";
import { OptionsStack } from "./optionsStack";
import { FilterListItemLabel } from "./filterListItemLabel";
import { NuIcon } from "../../common";
import { Stack } from "@mui/material";

export interface FiltersParams<V extends string = string, T = string> {
    label: string;
    options?: T[];
    value?: V[];
    onChange?: (value: V[]) => void;
}

export function ProcessingModeStack(props: FiltersParams<string, { name: string; Icon?: string }>): JSX.Element {
    const { options = [], value = [], onChange } = props;
    return (
        <OptionsStack {...props}>
            {options.map((option) => {
                const isSelected = value.includes(option.name);
                const onClick = (checked: boolean) => onChange(checked ? [...value, option.name] : value.filter((v) => v !== option.name));
                return (
                    <FilterListItem
                        key={option.name}
                        checked={isSelected}
                        onChange={onClick}
                        label={
                            <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                                <span>{option.displayableName}</span>
                                <option.Icon sx={{ fontSize: "1.2em", color: "inherit" }} />
                            </Stack>
                        }
                    />
                );
            })}
        </OptionsStack>
    );
}
