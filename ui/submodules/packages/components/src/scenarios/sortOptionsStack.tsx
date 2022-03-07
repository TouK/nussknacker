import { isDefaultSort, joinSort, splitSort } from "./itemsList";
import { FilterListItem } from "./filterListItem";
import React from "react";
import { FiltersParams } from "./selectFilter2";
import { ArrowDownward, ArrowUpward, ClearAll, Sort } from "@mui/icons-material";
import { OptionsStack } from "./optionsStack";
import { FilterListItemLabel } from "./filterListItemLabel";

export function SortOptionsStack(props: FiltersParams<{ name: string; icon?: string }>): JSX.Element {
    const { options, value, onChange } = props;
    return (
        <OptionsStack {...props} clearIcon={<ClearAll />}>
            {options?.map((option) => {
                const { key, order } = splitSort(value[0]);
                const isSelected = key === option.name;
                const isDesc = order === "desc";
                const isDefault = isDefaultSort(option.name, order);
                const onClick = () => {
                    const nextOrder = isSelected && isDesc ? "asc" : "desc";
                    onChange?.(isDefaultSort(option.name, nextOrder) ? null : [joinSort(option.name, nextOrder)]);
                };
                return (
                    <FilterListItem
                        color={isDefault ? "default" : "primary"}
                        key={option.name}
                        label={<FilterListItemLabel {...option} />}
                        icon={<Sort />}
                        checkedIcon={<ArrowUpward />}
                        indeterminateIcon={<ArrowDownward />}
                        indeterminate={isSelected && isDesc}
                        checked={isSelected}
                        onChange={onClick}
                    />
                );
            })}
        </OptionsStack>
    );
}
