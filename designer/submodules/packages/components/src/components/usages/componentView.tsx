import React, { useCallback, useMemo } from "react";
import { useParams } from "react-router-dom";
import { UsagesTable } from "./usagesTable";
import { useComponentUsagesWithStatus } from "../useComponentsQuery";
import { FiltersContextProvider, useFilterContext } from "../../common";
import { Breadcrumbs } from "./breadcrumbs";
import { UsagesFiltersModel } from "./usagesFiltersModel";
import { ActiveFilters } from "../../scenarios/filters/activeFilters";
import {sortBy, uniq} from "lodash";
import {useStatusDefinitions, useUserQuery} from "../../scenarios/useScenariosQuery";
import { FiltersPart } from "./filtersPart";
import { useTranslation } from "react-i18next";
import { ValueLinker } from "../../common/filters/filtersContext";

export function ComponentView(): JSX.Element {
    const valueLinker: ValueLinker<UsagesFiltersModel> = useCallback(
        (setNewValue) => (id, value) => {
            switch (id) {
                case "HIDE_SCENARIOS":
                    return value && setNewValue("HIDE_FRAGMENTS", false);
                case "HIDE_FRAGMENTS":
                    return value && setNewValue("HIDE_SCENARIOS", false);
            }
        },
        [],
    );

    return (
        <FiltersContextProvider<UsagesFiltersModel> getValueLinker={valueLinker}>
            <Component />
        </FiltersContextProvider>
    );
}

function Component(): JSX.Element {
    const { componentId } = useParams<"componentId">();
    const { data = [], isLoading } = useComponentUsagesWithStatus(componentId);
    const { data: statusDefinitions = [] } = useStatusDefinitions();
    const { t } = useTranslation();

    const { data: userData } = useUserQuery();
    const filterableValues = useMemo(
        () => ({
            CREATED_BY: uniq(["modifiedBy", "createdBy"].flatMap((k) => data.flatMap((v) => v[k])))
                .sort()
                .map((v) => ({ name: v })),
            CATEGORY: (userData?.categories || []).map((name) => ({ name })),
            status: sortBy(
                statusDefinitions.map((v) => ({ name: v.name, displayableName: v.displayableName, icon: v.icon, tooltip: v.tooltip })),
                (v) => v.displayableName || v.name
            ),
        }),
        [data, userData],
    );

    const statusFilterLabels = statusDefinitions.reduce((map, obj) => {map[obj.name] = obj.displayableName || obj.name; return map;}, {})
    const { activeKeys } = useFilterContext<UsagesFiltersModel>();

    const getLabel = useCallback(
        (name: keyof UsagesFiltersModel, value?: string | number) => {
            switch (name) {
                case "HIDE_FRAGMENTS":
                    return t("table.filter.desc.HIDE_FRAGMENTS", "Fragments hidden");
                case "HIDE_SCENARIOS":
                    return t("table.filter.desc.HIDE_SCENARIOS", "Scenarios hidden");
                case "STATUS":
                    return t("table.filter.status." + value, statusFilterLabels[value]);
            }

            if (value?.toString().length) {
                return value;
            }

            return name;
        },
        [t],
    );

    return (
        <>
            <Breadcrumbs />
            <FiltersPart isLoading={isLoading} filterableValues={filterableValues} />
            <ActiveFilters<UsagesFiltersModel> getLabel={getLabel} activeKeys={activeKeys.filter((k) => k !== "TEXT")} />
            <UsagesTable data={data} isLoading={isLoading} />
        </>
    );
}
