import React from "react";
import { FilterListItem } from "./filterListItem";
import { OptionsStack } from "./optionsStack";
import { FilterListItemLabel } from "./filterListItemLabel";

export interface FiltersParams<V extends string = string, T = string> {
    label: string;
    options?: T[];
    value?: V[];
    onChange?: (value: V[], checked?: boolean) => void;
}

export function SimpleOptionsStack(props: FiltersParams<string, { name: string; icon?: string }>): JSX.Element {
    const { options = [], value = [], onChange, label, ...passProps } = props;
    return (
        <OptionsStack label={label} {...props}>
            {options.map((option) => {
                const isSelected = value.includes(option.name);
                const onClick = (checked: boolean) =>
                    onChange(checked ? [...value, option.name] : value.filter((v) => v !== option.name), checked);
                return (
                    <FilterListItem
                        key={option.name}
                        checked={isSelected}
                        onChange={onClick}
                        label={<FilterListItemLabel {...option} />}
                        {...passProps}
                    />
                );
            })}
        </OptionsStack>
    );
}
