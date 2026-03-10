import { Divider } from "@mui/material";
import { flatten, uniq } from "lodash";
import { EventTrackingSelector, getEventTrackingProps } from "nussknackerUi/eventTracking";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { FiltersContextProvider } from "../common/filters/filtersContext";
import { ActiveFilters } from "../scenarios/filters/activeFilters";
import { FilterListItem } from "../scenarios/filters/filterListItem";
import { FilterMenu } from "../scenarios/filters/filterMenu";
import { OptionsStack } from "../scenarios/filters/optionsStack";
import { ProcessingModeStack } from "../scenarios/filters/processingModeStack";
import { QuickFilter } from "../scenarios/filters/quickFilter";
import { SimpleOptionsStack } from "../scenarios/filters/simpleOptionsStack";
import { processingModeItems } from "../scenarios/list/processingMode";
import { useUserQuery } from "../scenarios/useScenariosQuery";
import { ComponentTable } from "./componentTable";
import type { ComponentsFiltersModel } from "./filters/componentsFiltersModel";
import { COMPONENTS_FILTER } from "./filters/componentsFiltersModel";
import { useComponentsFilterContext } from "./filters/useComponentsFilterContext";
import { useComponentsQuery } from "./useComponentsQuery";

function CountFilterItem({ count }: { count: number }) {
    const { getFilter, setFilter } = useComponentsFilterContext();

    return (
        <FilterListItem
            checked={getFilter(COMPONENTS_FILTER.USAGES, true).includes(count)}
            onChange={(checked) => {
                const current = getFilter(COMPONENTS_FILTER.USAGES, true);
                if (checked) {
                    if (count > 0) {
                        return setFilter(COMPONENTS_FILTER.USAGES, [...current.filter((n) => n < 0 && Math.abs(n) > count), count]);
                    }
                    if (count < 0) {
                        return setFilter(COMPONENTS_FILTER.USAGES, [...current.filter((n) => n > 0 && n < Math.abs(count)), count]);
                    }
                    return setFilter(COMPONENTS_FILTER.USAGES, [...current, count]);
                }
                return setFilter(
                    COMPONENTS_FILTER.USAGES,
                    current.filter((value) => value !== count),
                );
            }}
            label={
                count < 0 ? (
                    <>
                        <strong style={{ fontSize: "1.3em" }}>{"<"}</strong> {-count}
                    </>
                ) : count > 0 ? (
                    <>
                        <strong style={{ fontSize: "1.3em" }}>{"≥"}</strong> {count}
                    </>
                ) : (
                    <>
                        <strong style={{ fontSize: "1.3em" }}>{"="}</strong> {count}
                    </>
                )
            }
            {...getEventTrackingProps({ selector: EventTrackingSelector.ComponentsByUsages })}
        />
    );
}

export function UsagesOptionsStack(): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useComponentsFilterContext();
    const otherFilters: Array<keyof ComponentsFiltersModel> = ["USAGES"];
    return (
        <OptionsStack
            label={t("table.filter.USAGE", "Usages")}
            options={otherFilters.map((name) => ({ name }))}
            value={otherFilters
                .flatMap<unknown>((k) => getFilter(k))
                .filter((v) => v === 0 || !!v)
                .map(toString)}
            onChange={(v) => otherFilters.forEach((k) => setFilter(k, v))}
        >
            <CountFilterItem count={1} />
            <CountFilterItem count={5} />
            <CountFilterItem count={10} />
            <CountFilterItem count={25} />
            <CountFilterItem count={100} />
            <Divider />
            <CountFilterItem count={-1} />
            <CountFilterItem count={-5} />
            <CountFilterItem count={-10} />
            <CountFilterItem count={-25} />
            <CountFilterItem count={-100} />
        </OptionsStack>
    );
}

export function FiltersPart({ isLoading, filterableValues }: { isLoading: boolean; filterableValues }) {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useComponentsFilterContext();

    return (
        <QuickFilter<ComponentsFiltersModel>
            isLoading={isLoading}
            filter="NAME"
            {...getEventTrackingProps({ selector: EventTrackingSelector.ComponentsByName })}
        >
            <FilterMenu label={t("table.filter.GROUP", "Group")} count={getFilter(COMPONENTS_FILTER.GROUP, true).length}>
                <SimpleOptionsStack
                    label={t("table.filter.GROUP", "Group")}
                    options={filterableValues["componentGroupName"]}
                    value={getFilter(COMPONENTS_FILTER.GROUP, true)}
                    onChange={setFilter(COMPONENTS_FILTER.GROUP)}
                    {...getEventTrackingProps({ selector: EventTrackingSelector.ComponentsByGroup })}
                />
            </FilterMenu>
            <FilterMenu
                label={t("table.filter.PROCESSING_MODE", "PROCESSING MODE")}
                count={getFilter(COMPONENTS_FILTER.PROCESSING_MODE, true).length}
            >
                <ProcessingModeStack
                    label={t("table.filter.PROCESSING_MODE", "PROCESSING MODE")}
                    options={filterableValues.processingModes}
                    value={getFilter(COMPONENTS_FILTER.PROCESSING_MODE, true)}
                    onChange={setFilter(COMPONENTS_FILTER.PROCESSING_MODE)}
                    {...getEventTrackingProps({
                        selector: EventTrackingSelector.ComponentsByProcessingMode,
                    })}
                />
            </FilterMenu>
            <FilterMenu label={t("table.filter.CATEGORY", "Category")} count={getFilter(COMPONENTS_FILTER.CATEGORY, true).length}>
                <SimpleOptionsStack
                    label={t("table.filter.CATEGORY", "Category")}
                    options={filterableValues["categories"]}
                    value={getFilter(COMPONENTS_FILTER.CATEGORY, true)}
                    onChange={setFilter(COMPONENTS_FILTER.CATEGORY)}
                    {...getEventTrackingProps({
                        selector: EventTrackingSelector.ComponentsByCategory,
                    })}
                />
            </FilterMenu>
            <FilterMenu label={t("table.filter.USAGE", "Usages")} count={getFilter(COMPONENTS_FILTER.USAGES, true).length}>
                <UsagesOptionsStack />
            </FilterMenu>
        </QuickFilter>
    );
}

export function Components(): JSX.Element {
    const { data = [], isLoading } = useComponentsQuery();
    const { data: userData } = useUserQuery();
    const { t } = useTranslation();

    const filterableKeys = useMemo(() => ["categories", "componentGroupName", "processingModes"], []);
    const filterableValues = useMemo(() => {
        const entries = filterableKeys.map((k) => [
            k,
            uniq(flatten(data.map((v) => v[k])))
                .sort()
                .map((v) => ({ name: v })),
        ]);
        return Object.fromEntries([
            ...entries,
            ["processingModes", processingModeItems],
            ["categories", (userData?.categories || []).map((name) => ({ name }))],
        ]);
    }, [data, filterableKeys, userData?.categories]);

    const getLabel = useCallback(
        (name: keyof ComponentsFiltersModel, value?: string | number) => {
            switch (name) {
                case "SHOW_ARCHIVED":
                    return t("table.filter.desc.SHOW_ARCHIVED", "Archived visible");
                case "USAGES":
                    return t("table.filter.desc.USAGES", "Used {{val}} times", {
                        val: typeof value === "string" || value === 0 ? `${value}` : value <= 0 ? `< ${-value}` : `≥ ${value}`,
                        interpolation: { escapeValue: false },
                    });
                case "PROCESSING_MODE":
                    return processingModeItems.find((processingModeItem) => processingModeItem.name === value).displayableName;
            }

            if (value?.toString().length) {
                return value.toString();
            }

            return name;
        },
        [t],
    );

    const { activeKeys } = useComponentsFilterContext();

    return (
        <>
            <FiltersPart isLoading={isLoading} filterableValues={filterableValues} />
            <ActiveFilters getLabel={getLabel} activeKeys={activeKeys.filter((k) => k !== COMPONENTS_FILTER.NAME)} />
            <ComponentTable data={data} isLoading={isLoading} />
        </>
    );
}

export function ComponentsView(): JSX.Element {
    return (
        <FiltersContextProvider<ComponentsFiltersModel>>
            <Components />
        </FiltersContextProvider>
    );
}
