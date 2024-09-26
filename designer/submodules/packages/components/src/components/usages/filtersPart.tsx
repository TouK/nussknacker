import { UsagesFiltersModel, UsagesFiltersModelType, UsagesFiltersUsageType, UsagesFiltersValues } from "./usagesFiltersModel";
import { useFilterContext } from "../../common";
import { QuickFilter } from "../../scenarios/filters/quickFilter";
import { FilterMenu } from "../../scenarios/filters/filterMenu";
import { SimpleOptionsStack } from "../../scenarios/filters/simpleOptionsStack";
import { StatusOptionsStack } from "../../scenarios/filters/typeOptionsStack";
import { OptionsStack } from "../../scenarios/filters/optionsStack";
import { FilterListItem } from "../../scenarios/filters/filterListItem";
import React from "react";
import { useTranslation } from "react-i18next";
import { Divider, Stack } from "@mui/material";
import { xor } from "lodash";
import { EventTrackingSelector, getEventTrackingProps } from "nussknackerUi/eventTracking";

interface FiltersPartProps {
    isLoading: boolean;
    filterableValues: UsagesFiltersValues;
}

export function FiltersPart({ isLoading, filterableValues }: FiltersPartProps): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<UsagesFiltersModel>();

    const otherFilters: Array<keyof UsagesFiltersModel> = ["TYPE", "USAGE_TYPE"];

    return (
        <QuickFilter<UsagesFiltersModel>
            isLoading={isLoading}
            filter="TEXT"
            {...getEventTrackingProps({ selector: EventTrackingSelector.ComponentUsagesByName })}
        >
            <Stack direction="row" spacing={1} p={1} alignItems="center" divider={<Divider orientation="vertical" flexItem />}>
                <FilterMenu label={t("table.filter.STATUS", "Status")} count={getFilter("STATUS", true).length}>
                    <StatusOptionsStack
                        options={filterableValues["STATUS"]}
                        withArchived={false}
                        {...getEventTrackingProps({
                            selector: EventTrackingSelector.ComponentUsagesByStatus,
                        })}
                    />
                </FilterMenu>
                <FilterMenu label={t("table.filter.CATEGORY", "Category")} count={getFilter("CATEGORY", true).length}>
                    <SimpleOptionsStack
                        label={t("table.filter.CATEGORY", "Category")}
                        options={filterableValues["CATEGORY"]}
                        value={getFilter("CATEGORY", true)}
                        onChange={setFilter("CATEGORY")}
                        {...getEventTrackingProps({
                            selector: EventTrackingSelector.ComponentUsagesByCategory,
                        })}
                    />
                </FilterMenu>
                <FilterMenu label={t("table.filter.AUTHOR", "Author")} count={getFilter("CREATED_BY", true).length}>
                    <SimpleOptionsStack
                        label={t("table.filter.AUTHOR", "Author")}
                        options={filterableValues["CREATED_BY"]}
                        value={getFilter("CREATED_BY", true)}
                        onChange={setFilter("CREATED_BY")}
                        {...getEventTrackingProps({
                            selector: EventTrackingSelector.ComponentUsagesByAuthor,
                        })}
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
                            checkboxProps={{
                                color: "default",
                            }}
                            checked={getFilter("TYPE", true)?.includes(UsagesFiltersModelType.SCENARIOS)}
                            onChange={() => setFilter("TYPE", xor([UsagesFiltersModelType.SCENARIOS], getFilter("TYPE", true)))}
                            label={t("table.filter.SHOW_SCENARIOS", "Show scenarios")}
                            {...getEventTrackingProps({
                                selector: EventTrackingSelector.ComponentUsagesByOther,
                            })}
                        />
                        <FilterListItem
                            checkboxProps={{
                                color: "default",
                            }}
                            checked={getFilter("TYPE", true)?.includes(UsagesFiltersModelType.FRAGMENTS)}
                            onChange={() => setFilter("TYPE", xor([UsagesFiltersModelType.FRAGMENTS], getFilter("TYPE", true)))}
                            label={t("table.filter.SHOW_FRAGMENTS", "Show fragments")}
                            {...getEventTrackingProps({
                                selector: EventTrackingSelector.ComponentUsagesByOther,
                            })}
                        />
                        <Divider />
                        <FilterListItem
                            checkboxProps={{
                                color: "secondary",
                            }}
                            checked={getFilter("USAGE_TYPE", true)?.includes(UsagesFiltersUsageType.INDIRECT)}
                            onChange={() => setFilter("USAGE_TYPE", xor([UsagesFiltersUsageType.INDIRECT], getFilter("USAGE_TYPE", true)))}
                            label={t("table.filter.SHOW_INDIRECT", "Show indirect usage")}
                            {...getEventTrackingProps({
                                selector: EventTrackingSelector.ComponentUsagesByOther,
                            })}
                        />
                        <FilterListItem
                            checkboxProps={{
                                color: "primary",
                            }}
                            checked={getFilter("USAGE_TYPE", true)?.includes(UsagesFiltersUsageType.DIRECT)}
                            onChange={() => setFilter("USAGE_TYPE", xor([UsagesFiltersUsageType.DIRECT], getFilter("USAGE_TYPE", true)))}
                            label={t("table.filter.SHOW_DIRECT", "Show direct usage")}
                            {...getEventTrackingProps({
                                selector: EventTrackingSelector.ComponentUsagesByOther,
                            })}
                        />
                    </OptionsStack>
                </FilterMenu>
            </Stack>
        </QuickFilter>
    );
}
