import { isDefaultSort, joinSort, SortKey, splitSort } from "../list/itemsList";
import { FilterListItem } from "./filterListItem";
import React, { useCallback, useMemo } from "react";
import { ArrowDownward, ArrowUpward, Sort } from "@mui/icons-material";
import { OptionsStack } from "./optionsStack";
import { FilterListItemLabel } from "./filterListItemLabel";
import { FiltersParams } from "./simpleOptionsStack";

export function SortOptionsStack(props: FiltersParams<SortKey, { name: string; icon?: string }>): JSX.Element {
    const { options, value, onChange } = props;
    const valueElement = value[0];
    const sortBy = useMemo(() => splitSort(valueElement), [valueElement]);
    return (
        <OptionsStack {...props} clearIcon={<Sort />}>
            {options?.map((option) => (
                <SortOption key={option.name} option={option} value={sortBy} onChange={onChange} />
            ))}
        </OptionsStack>
    );
}

function SortOption(props: {
    value: { key: string; order: "asc" | "desc" };
    option: { name: string; icon?: string };
    onChange: (value: SortKey[]) => void;
}) {
    const {
        value: { key, order },
        option: { icon, name },
        onChange,
    } = props;
    const isSelected = key === name;
    const isDesc = order === "desc";
    const isDefault = isDefaultSort(name, order);
    const onClick = useCallback(() => {
        const nextOrder = isSelected && isDesc ? "asc" : "desc";
        onChange?.(isDefaultSort(name, nextOrder) ? null : [joinSort(name, nextOrder)]);
    }, [isDesc, isSelected, onChange, name]);
    return (
        <FilterListItem
            color={isDefault ? "default" : "error"}
            label={<FilterListItemLabel name={name} icon={icon} />}
            icon={<Sort />}
            checkedIcon={<ArrowUpward />}
            indeterminateIcon={<ArrowDownward />}
            indeterminate={isSelected && isDesc}
            checked={isSelected}
            onChange={onClick}
            touched={!isDefault && isSelected}
        />
    );
}
