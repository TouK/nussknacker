import { useFilterContext } from "../../common";
import React from "react";
import { useTranslation } from "react-i18next";
import {ScenariosFiltersModel, ScenariosFiltersModelType} from "./scenariosFiltersModel";
import {FilterListItem, FilterListItemSwitch} from "./filterListItem";
import { OptionsStack } from "./optionsStack";
import { Divider } from "@mui/material";
import { some, xor } from "lodash";
import {FiltersParams} from "./simpleOptionsStack";
import {FilterListItemLabel} from "./filterListItemLabel";

export function OtherOptionsStack(): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<ScenariosFiltersModel>();
    const otherFilters: Array<keyof ScenariosFiltersModel> = ["TYPE"];
    const getTypeFilter = () => getFilter("TYPE", true);

    return (
        <OptionsStack
            label={t("table.filter.other", "Other")}
            options={otherFilters.map((name) => ({ name }))}
            value={otherFilters
                .flatMap((k) => getFilter(k))
                .filter(Boolean)
                .map(toString)}
            onChange={(v) => otherFilters.forEach((k) => setFilter(k, v))}
        >
            <FilterListItem
                color="default"
                checked={getFilter("TYPE", true)?.includes(ScenariosFiltersModelType.SCENARIOS)}
                onChange={() => setFilter("TYPE", xor([ScenariosFiltersModelType.SCENARIOS], getTypeFilter()))}
                label={t("table.filter.SCENARIOS", "Scenarios")}
            />
            <FilterListItem
                color="default"
                checked={getFilter("TYPE", true)?.includes(ScenariosFiltersModelType.FRAGMENTS)}
                onChange={() => setFilter("TYPE", xor([ScenariosFiltersModelType.FRAGMENTS], getTypeFilter()))}
                label={t("table.filter.FRAGMENTS", "Fragments")}
            />
        </OptionsStack>
    );
}

export interface StatusFiltersParams<V extends string = string, T = string> {
    options?: T[];
    withArchived: boolean
}

export function StatusOptionsStack(props: StatusFiltersParams<string, { name: string; displayableName: string; icon?: string; tooltip?: string }>): JSX.Element {
    const { options = [], withArchived } = props;
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<ScenariosFiltersModel>();
    const filters: Array<keyof ScenariosFiltersModel> = ["ARCHIVED", "STATUS"];

    const value = getFilter("STATUS", true);
    const onChange = setFilter("STATUS");

    return (
        <OptionsStack
            label={t("table.filter.STATUS", "Status")}
            options={filters.map((name) => ({ name }))}
            value={filters
                .flatMap((k: keyof ScenariosFiltersModel) => getFilter(k))
                .filter(Boolean)
                .map(toString)}
            onChange={(v) => filters.forEach((k: keyof ScenariosFiltersModel) => setFilter(k, v))}
        >
            {options.map((option) => {
                const isSelected = value.includes(option.name);
                const onClick = (checked: boolean) => onChange(checked ? [...value, option.name] : value.filter((v) => v !== option.name));
                return (
                    <FilterListItem key={option.name} checked={isSelected} onChange={onClick} label={<FilterListItemLabel {...option} />} />
                );
            })}
            {withArchived ? (
                <>
                    <Divider />
                    <FilterListItemSwitch
                        checked={getFilter("ARCHIVED") === true}
                        onChange={(checked) => setFilter("ARCHIVED", checked)}
                        label={t("table.filter.ARCHIVED", "Archived")}
                    />
                </>
            ) : null }
        </OptionsStack>
    );
}
