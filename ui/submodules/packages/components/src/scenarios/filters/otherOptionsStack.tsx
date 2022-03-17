import { useFilterContext } from "../../common";
import React from "react";
import { useTranslation } from "react-i18next";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";
import { FilterListItem } from "./filterListItem";
import { OptionsStack } from "./optionsStack";

export function OtherOptionsStack(): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<ScenariosFiltersModel>();
    const otherFilters: Array<keyof ScenariosFiltersModel> = ["HIDE_SCENARIOS", "HIDE_FRAGMENTS", "HIDE_ACTIVE", "SHOW_ARCHIVED"];

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
