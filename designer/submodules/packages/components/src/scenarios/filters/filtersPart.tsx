import { Divider, Stack } from "@mui/material";
import { flatten, sortBy, uniq } from "lodash";
import { EventTrackingSelector, getEventTrackingProps } from "nussknackerUi/eventTracking";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import type { RowType } from "../list/listPart";
import { processingModeItems } from "../list/processingMode";
import { useScenarioLabelsQuery, useScenariosWithCategoryVisible, useStatusDefinitions, useUserQuery } from "../useScenariosQuery";
import { ActiveFilters } from "./activeFilters";
import { useScenariosFilterContext } from "./common/useScenariosFilterContext";
import { FilterMenu } from "./filterMenu";
import { ProcessingModeStack } from "./processingModeStack";
import { QuickFilter } from "./quickFilter";
import type { ScenariosFiltersModel } from "./scenariosFiltersModel";
import { SCENARIOS_FILTER } from "./scenariosFiltersModel";
import { ScenariosFiltersModelType } from "./scenariosFiltersModel";
import { SimpleOptionsStack } from "./simpleOptionsStack";
import { SortOptionsStack } from "./sortOptionsStack";
import { TypeOptionsStack, StatusOptionsStack } from "./typeOptionsStack";

export function FiltersPart({ withSort, isLoading, data = [] }: { data: RowType[]; isLoading?: boolean; withSort?: boolean }): JSX.Element {
    const { t } = useTranslation();
    const { data: userData } = useUserQuery();
    const { data: statusDefinitions = [] } = useStatusDefinitions();
    const { data: availableLabels } = useScenarioLabelsQuery();
    const { withCategoriesVisible } = useScenariosWithCategoryVisible();

    const filterableKeys = useMemo(() => ["createdBy", "modifiedBy"], []);
    const filterableValues = useMemo(() => {
        const entries = filterableKeys.map((k) => [k, uniq(flatten(data.map((v) => v[k]))).sort()]);
        return {
            ...Object.fromEntries(entries),
            author: uniq(["modifiedBy", "createdBy"].flatMap((k) => data.flatMap((v) => v[k])))
                .sort()
                .map((v) => ({ name: v })),
            status: sortBy(statusDefinitions, (v) => v.displayableName),
            processCategory: (userData?.categories || []).map((name) => ({ name })),
            label: (availableLabels?.labels || []).map((name) => ({ name })),
            processingMode: processingModeItems,
        };
    }, [availableLabels?.labels, data, filterableKeys, statusDefinitions, userData?.categories]);

    const statusFilterLabels = statusDefinitions.reduce((map, obj) => {
        map[obj.name] = obj.displayableName;
        return map;
    }, {});
    const { getFilter, setFilter, activeKeys } = useScenariosFilterContext();

    const getLabel = useCallback(
        (name: keyof ScenariosFiltersModel, value?: string | number) => {
            switch (name) {
                case "TYPE":
                    switch (value) {
                        case ScenariosFiltersModelType.FRAGMENTS:
                            return t("table.filter.FRAGMENTS", "Fragments");
                        case ScenariosFiltersModelType.SCENARIOS:
                            return t("table.filter.SCENARIOS", "Scenarios");
                    }
                    break;
                case "ARCHIVED":
                    return t("table.filter.ARCHIVED", "Archived");
                case "STATUS":
                    return t("table.filter.status." + value, statusFilterLabels[value]);
                case "PROCESSING_MODE":
                    return processingModeItems.find((processingModeItem) => processingModeItem.name === value).displayableName;
            }

            if (value?.toString().length) {
                return value;
            }

            return name;
        },
        [t, statusFilterLabels],
    );

    return (
        <>
            <QuickFilter<ScenariosFiltersModel>
                isLoading={isLoading}
                filter="NAME"
                {...getEventTrackingProps({ selector: EventTrackingSelector.ScenariosByName })}
            >
                <Stack direction="row" spacing={1} p={1} alignItems="center" divider={<Divider orientation="vertical" flexItem />}>
                    <FilterMenu label={t("table.filter.STATUS", "Status")} count={getFilter(SCENARIOS_FILTER.STATUS, true).length}>
                        <StatusOptionsStack
                            options={filterableValues.status}
                            withArchived={true}
                            {...getEventTrackingProps({
                                selector: EventTrackingSelector.ScenariosByStatus,
                            })}
                        />
                    </FilterMenu>
                    <FilterMenu
                        label={t("table.filter.PROCESSING_MODE", "PROCESSING MODE")}
                        count={getFilter(SCENARIOS_FILTER.PROCESSING_MODE, true).length}
                    >
                        <ProcessingModeStack
                            label={t("table.filter.PROCESSING_MODE", "PROCESSING MODE")}
                            options={filterableValues.processingMode}
                            value={getFilter(SCENARIOS_FILTER.PROCESSING_MODE, true)}
                            onChange={setFilter(SCENARIOS_FILTER.PROCESSING_MODE)}
                            {...getEventTrackingProps({
                                selector: EventTrackingSelector.ScenariosByProcessingMode,
                            })}
                        />
                    </FilterMenu>
                    {withCategoriesVisible && (
                        <FilterMenu
                            label={t("table.filter.CATEGORY", "Category")}
                            count={getFilter(SCENARIOS_FILTER.CATEGORY, true).length}
                        >
                            <SimpleOptionsStack
                                label={t("table.filter.CATEGORY", "Category")}
                                options={filterableValues.processCategory}
                                value={getFilter(SCENARIOS_FILTER.CATEGORY, true)}
                                onChange={setFilter(SCENARIOS_FILTER.CATEGORY)}
                                {...getEventTrackingProps({
                                    selector: EventTrackingSelector.ScenariosByCategory,
                                })}
                            />
                        </FilterMenu>
                    )}
                    <FilterMenu label={t("table.filter.LABEL", "Label")} count={getFilter(SCENARIOS_FILTER.LABEL, true).length}>
                        <SimpleOptionsStack
                            label={t("table.filter.LABEL", "Label")}
                            options={filterableValues.label}
                            value={getFilter(SCENARIOS_FILTER.LABEL, true)}
                            onChange={setFilter(SCENARIOS_FILTER.LABEL)}
                            {...getEventTrackingProps({
                                selector: EventTrackingSelector.ScenariosByLabel,
                            })}
                        />
                    </FilterMenu>
                    <FilterMenu label={t("table.filter.CREATED_BY", "Author")} count={getFilter(SCENARIOS_FILTER.CREATED_BY, true).length}>
                        <SimpleOptionsStack
                            label={t("table.filter.CREATED_BY", "Author")}
                            options={filterableValues.author}
                            value={getFilter(SCENARIOS_FILTER.CREATED_BY, true)}
                            onChange={setFilter(SCENARIOS_FILTER.CREATED_BY)}
                            {...getEventTrackingProps({
                                selector: EventTrackingSelector.ScenariosByAuthor,
                            })}
                        />
                    </FilterMenu>
                    <FilterMenu label={t("table.filter.TYPE", "Type")} count={getFilter(SCENARIOS_FILTER.TYPE, true).length}>
                        <TypeOptionsStack />
                    </FilterMenu>
                    {withSort ? (
                        <FilterMenu label={t("table.filter.SORT_BY", "Sort")}>
                            <SortOptionsStack
                                label={t("table.filter.SORT_BY", "Sort")}
                                options={["createdAt", "modificationDate", "name"].map((name) => ({ name }))}
                                value={getFilter(SCENARIOS_FILTER.SORT_BY, true)}
                                onChange={setFilter(SCENARIOS_FILTER.SORT_BY)}
                                {...getEventTrackingProps({
                                    selector: EventTrackingSelector.ScenariosBySortOption,
                                })}
                            />
                        </FilterMenu>
                    ) : null}
                </Stack>
            </QuickFilter>
            <ActiveFilters
                getLabel={getLabel}
                activeKeys={activeKeys.filter((k) => k !== SCENARIOS_FILTER.NAME && k !== SCENARIOS_FILTER.SORT_BY)}
            />
        </>
    );
}
