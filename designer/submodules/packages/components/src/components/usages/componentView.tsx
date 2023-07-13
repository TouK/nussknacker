import React, { useCallback, useMemo } from "react";
import { useParams } from "react-router-dom";
import { UsagesTable } from "./usagesTable";
import { useComponentUsagesWithStatus } from "../useComponentsQuery";
import { FiltersContextProvider, useFilterContext } from "../../common";
import { Breadcrumbs } from "./breadcrumbs";
import { UsagesFiltersModel, UsagesFiltersModelType, UsagesFiltersUsageType, UsagesFiltersValues } from "./usagesFiltersModel";
import { ActiveFilters, getRandomColorByType } from "../../scenarios/filters/activeFilters";
import { sortBy, uniq } from "lodash";
import { useStatusDefinitions, useUserQuery } from "../../scenarios/useScenariosQuery";
import { FiltersPart } from "./filtersPart";
import { useTranslation } from "react-i18next";
import { useTheme } from "@mui/material";

export function ComponentView(): JSX.Element {
    return (
        <FiltersContextProvider<UsagesFiltersModel>>
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
    const filterableValues = useMemo<UsagesFiltersValues>(
        () => ({
            CREATED_BY: uniq(["modifiedBy", "createdBy"].flatMap((k) => data.flatMap((v) => v[k])))
                .sort()
                .map((v) => ({ name: v })),
            CATEGORY: (userData?.categories || []).map((name) => ({ name })),
            STATUS: sortBy(statusDefinitions, (v) => v.displayableName),
        }),
        [data, statusDefinitions, userData?.categories],
    );

    const statusFilterLabels = statusDefinitions.reduce((map, obj) => {
        map[obj.name] = obj.displayableName;
        return map;
    }, {});
    const { activeKeys } = useFilterContext<UsagesFiltersModel>();

    const getLabel = useCallback(
        (name: keyof UsagesFiltersModel, value?: string | number) => {
            switch (name) {
                case "TYPE":
                    switch (value) {
                        case UsagesFiltersModelType.FRAGMENTS:
                            return t("table.filter.FRAGMENTS", "Fragments");
                        case UsagesFiltersModelType.SCENARIOS:
                            return t("table.filter.SCENARIOS", "Scenarios");
                    }
                    break;
                case "USAGE_TYPE":
                    switch (value) {
                        case UsagesFiltersUsageType.INDIRECT:
                            return t("table.filter.INDIRECT", "Indirect usage");
                        case UsagesFiltersUsageType.STRAIGHT:
                            return t("table.filter.STRAIGHT", "Straight usage");
                    }
                    break;
                case "STATUS":
                    return t("table.filter.status." + value, statusFilterLabels[value]);
            }

            if (value?.toString().length) {
                return value;
            }

            return name;
        },
        [statusFilterLabels, t],
    );

    const theme = useTheme();
    const getColor = useCallback(
        (type, value) => {
            switch (type) {
                case "USAGE_TYPE":
                    switch (value) {
                        case UsagesFiltersUsageType.INDIRECT:
                            return theme.palette.secondary.main;
                        case UsagesFiltersUsageType.STRAIGHT:
                            return theme.palette.primary.main;
                    }
            }
            return getRandomColorByType(type);
        },
        [theme.palette.primary.main, theme.palette.secondary.main],
    );

    return (
        <>
            <Breadcrumbs />
            <FiltersPart isLoading={isLoading} filterableValues={filterableValues} />
            <ActiveFilters<UsagesFiltersModel>
                getLabel={getLabel}
                getColor={getColor}
                activeKeys={activeKeys.filter((k) => k !== "TEXT")}
            />
            <UsagesTable data={data} isLoading={isLoading} />
        </>
    );
}
