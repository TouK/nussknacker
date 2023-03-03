import { useFilterContext } from "../../common";
import React from "react";
import { useTranslation } from "react-i18next";
import {ScenariosFiltersModel, ScenariosFiltersModelDeployed, ScenariosFiltersModelType} from "./scenariosFiltersModel";
import {FilterListItem, FilterListItemSwitch} from "./filterListItem";
import { OptionsStack } from "./optionsStack";
import { Divider } from "@mui/material";
import { some, xor } from "lodash";

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

export function StatusOptionsStack(): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<ScenariosFiltersModel>();
    const filters: Array<keyof ScenariosFiltersModel> = ["ARCHIVED", "DEPLOYED"];
    const getDeployedFilter = () => getFilter("DEPLOYED", true);

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
            <FilterListItem
                color="default"
                checked={getFilter("DEPLOYED", true)?.includes(ScenariosFiltersModelDeployed.DEPLOYED)}
                onChange={() => setFilter("DEPLOYED", xor([ScenariosFiltersModelDeployed.DEPLOYED], getDeployedFilter()))}
                label={t("table.filter.DEPLOYED", "Deployed")}
            />
            <FilterListItem
                color="default"
                checked={getFilter("DEPLOYED", true)?.includes(ScenariosFiltersModelDeployed.NOT_DEPLOYED)}
                onChange={() => setFilter("DEPLOYED", xor([ScenariosFiltersModelDeployed.NOT_DEPLOYED], getDeployedFilter()))}
                label={t("table.filter.NOT_DEPLOYED", "Not deployed")}
            />
            <Divider />
            <FilterListItemSwitch
                checked={getFilter("ARCHIVED") === true}
                onChange={(checked) => setFilter("ARCHIVED", checked)}
                label={t("table.filter.ARCHIVED", "Archived")}
            />
        </OptionsStack>
    );
}
