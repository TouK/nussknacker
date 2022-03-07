import React from "react";
import { FilterListItem } from "./filterListItem";
import { OptionsStack } from "./optionsStack";
import { FilterListItemLabel } from "./filterListItemLabel";

export interface FiltersParams<T = string> {
    label: string;
    options: T[];
    value?: string[];
    onChange?: (value: string[]) => void;
}

export function SimpleOptionsStack(props: FiltersParams<{ name: string; icon?: string }>): JSX.Element {
    const { options, value, onChange } = props;
    return (
        <OptionsStack {...props}>
            {options?.map((option) => {
                const isSelected = value.includes(option.name);
                const onClick = (checked: boolean) => onChange(checked ? [...value, option.name] : value.filter((v) => v !== option.name));
                return (
                    <FilterListItem key={option.name} checked={isSelected} onChange={onClick} label={<FilterListItemLabel {...option} />} />
                );
            })}
        </OptionsStack>
    );
}
