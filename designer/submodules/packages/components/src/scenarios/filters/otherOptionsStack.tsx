import { useFilterContext } from "../../common";
import React from "react";
import { useTranslation } from "react-i18next";
import { ScenariosFiltersModel, ScenariosFiltersModelDeployed } from "./scenariosFiltersModel";
import { FilterListItem } from "./filterListItem";
import { OptionsStack } from "./optionsStack";
import { Divider } from "@mui/material";
import { some, xor } from "lodash";

export function OtherOptionsStack(): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<ScenariosFiltersModel>();
    const otherFilters: Array<keyof ScenariosFiltersModel> = ["HIDE_SCENARIOS", "HIDE_FRAGMENTS"];

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
                invert
                color="default"
                checked={getFilter("HIDE_SCENARIOS") === true}
                onChange={(checked) => setFilter("HIDE_SCENARIOS", checked)}
                label={t("table.filter.SHOW_SCENARIOS", "Show scenarios")}
            />
            <FilterListItem
                invert
                color="default"
                checked={getFilter("HIDE_FRAGMENTS") === true}
                onChange={(checked) => setFilter("HIDE_FRAGMENTS", checked)}
                label={t("table.filter.SHOW_FRAGMENTS", "Show fragments")}
            />
        </OptionsStack>
    );
}

export function StatusOptionsStack(): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<ScenariosFiltersModel>();
    const filters: Array<keyof ScenariosFiltersModel> = ["HIDE_ACTIVE", "SHOW_ARCHIVED", "DEPLOYED"];

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
                onChange={() => setFilter("DEPLOYED", xor([ScenariosFiltersModelDeployed.DEPLOYED], getFilter("DEPLOYED", true)))}
                label={t("table.filter.DEPLOYED", "Deployed")}
            />
            <FilterListItem
                color="default"
                checked={getFilter("DEPLOYED", true)?.includes(ScenariosFiltersModelDeployed.NOT_DEPLOYED)}
                onChange={() => setFilter("DEPLOYED", xor([ScenariosFiltersModelDeployed.NOT_DEPLOYED], getFilter("DEPLOYED", true)))}
                label={t("table.filter.NOT_DEPLOYED", "Not deployed")}
            />
            <Divider />
            <FilterListItem
                invert
                color="default"
                checked={getFilter("HIDE_ACTIVE") === true}
                onChange={(checked) => setFilter("HIDE_ACTIVE", checked)}
                label={t("table.filter.SHOW_ACTIVE", "Show active")}
            />
            <FilterListItem
                checked={getFilter("SHOW_ARCHIVED") === true}
                onChange={(checked) => setFilter("SHOW_ARCHIVED", checked)}
                label={t("table.filter.SHOW_ARCHIVED", "Show archived")}
            />
        </OptionsStack>
    );
}
