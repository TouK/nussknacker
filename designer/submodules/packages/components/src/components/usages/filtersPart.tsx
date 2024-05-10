import { UsagesFiltersModel, UsagesFiltersModelType, UsagesFiltersUsageType, UsagesFiltersValues } from "./usagesFiltersModel";
import { useFilterContext } from "../../common";
import { QuickFilter } from "../../scenarios/filters/quickFilter";
import { FilterMenu } from "../../scenarios/filters/filterMenu";
import { SimpleOptionsStack } from "../../scenarios/filters/simpleOptionsStack";
import { StatusOptionsStack } from "../../scenarios/filters/otherOptionsStack";
import { OptionsStack } from "../../scenarios/filters/optionsStack";
import { FilterListItem } from "../../scenarios/filters/filterListItem";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { Divider, Stack } from "@mui/material";
import { xor } from "lodash";
import { useTrackFilterSelect } from "../../common/hooks/useTrackFilterSet";

interface FiltersPartProps {
    isLoading: boolean;
    filterableValues: UsagesFiltersValues;
}

export function FiltersPart({ isLoading, filterableValues }: FiltersPartProps): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<UsagesFiltersModel>();
    const { withTrackFilterSelect } = useTrackFilterSelect();

    const otherFilters: Array<keyof UsagesFiltersModel> = ["TYPE", "USAGE_TYPE"];

    const handleOtherFilterChange = useCallback(
        (checked: boolean, filter: keyof UsagesFiltersModel, filterTypes: (UsagesFiltersModelType | UsagesFiltersUsageType)[]) =>
            withTrackFilterSelect({ type: "FILTER_COMPONENT_USAGES_BY_OTHER" }, setFilter(filter))(
                xor(filterTypes, getFilter(filter, true)),
                checked,
            ),
        [getFilter, setFilter, withTrackFilterSelect],
    );

    return (
        <QuickFilter<UsagesFiltersModel> isLoading={isLoading} filter="TEXT" trackingEvent={"SEARCH_COMPONENT_USAGES_BY_NAME"}>
            <Stack direction="row" spacing={1} p={1} alignItems="center" divider={<Divider orientation="vertical" flexItem />}>
                <FilterMenu label={t("table.filter.STATUS", "Status")} count={getFilter("STATUS", true).length}>
                    <StatusOptionsStack options={filterableValues["STATUS"]} withArchived={false} />
                </FilterMenu>
                <FilterMenu label={t("table.filter.CATEGORY", "Category")} count={getFilter("CATEGORY", true).length}>
                    <SimpleOptionsStack
                        label={t("table.filter.CATEGORY", "Category")}
                        options={filterableValues["CATEGORY"]}
                        value={getFilter("CATEGORY", true)}
                        onChange={withTrackFilterSelect({ type: "FILTER_COMPONENT_USAGES_BY_CATEGORY" }, setFilter("CATEGORY"))}
                    />
                </FilterMenu>
                <FilterMenu label={t("table.filter.AUTHOR", "Author")} count={getFilter("CREATED_BY", true).length}>
                    <SimpleOptionsStack
                        label={t("table.filter.AUTHOR", "Author")}
                        options={filterableValues["CREATED_BY"]}
                        value={getFilter("CREATED_BY", true)}
                        onChange={withTrackFilterSelect({ type: "FILTER_COMPONENT_USAGES_BY_AUTHOR" }, setFilter("CREATED_BY"))}
                    />
                </FilterMenu>
                <FilterMenu
                    label={t("table.filter.other", "Other")}
                    count={getFilter("TYPE", true).length + getFilter("USAGE_TYPE", true).length}
                >
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
                            checked={getFilter("TYPE", true)?.includes(UsagesFiltersModelType.SCENARIOS)}
                            onChange={(checked) => handleOtherFilterChange(checked, "TYPE", [UsagesFiltersModelType.SCENARIOS])}
                            label={t("table.filter.SHOW_SCENARIOS", "Show scenarios")}
                        />
                        <FilterListItem
                            color="default"
                            checked={getFilter("TYPE", true)?.includes(UsagesFiltersModelType.FRAGMENTS)}
                            onChange={(checked) => handleOtherFilterChange(checked, "TYPE", [UsagesFiltersModelType.FRAGMENTS])}
                            label={t("table.filter.SHOW_FRAGMENTS", "Show fragments")}
                        />
                        <Divider />
                        <FilterListItem
                            color="secondary"
                            checked={getFilter("USAGE_TYPE", true)?.includes(UsagesFiltersUsageType.INDIRECT)}
                            onChange={(checked) => handleOtherFilterChange(checked, "USAGE_TYPE", [UsagesFiltersUsageType.INDIRECT])}
                            label={t("table.filter.SHOW_INDIRECT", "Show indirect usage")}
                        />
                        <FilterListItem
                            color="primary"
                            checked={getFilter("USAGE_TYPE", true)?.includes(UsagesFiltersUsageType.DIRECT)}
                            onChange={(checked) => handleOtherFilterChange(checked, "USAGE_TYPE", [UsagesFiltersUsageType.DIRECT])}
                            label={t("table.filter.SHOW_DIRECT", "Show direct usage")}
                        />
                    </OptionsStack>
                </FilterMenu>
            </Stack>
        </QuickFilter>
    );
}
