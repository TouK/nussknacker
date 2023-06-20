import React, { useCallback, useMemo } from "react";
import { flatten, uniq, uniqBy, sortBy } from "lodash";
import { useFilterContext } from "../../common";
import { ScenariosFiltersModel, ScenariosFiltersModelType } from "./scenariosFiltersModel";
import { useStatusDefinitions, useUserQuery } from "../useScenariosQuery";
import { QuickFilter } from "./quickFilter";
import { FilterMenu } from "./filterMenu";
import { SimpleOptionsStack } from "./simpleOptionsStack";
import { OtherOptionsStack, StatusOptionsStack } from "./otherOptionsStack";
import { SortOptionsStack } from "./sortOptionsStack";
import { ActiveFilters } from "./activeFilters";
import { RowType } from "../list/listPart";
import { Divider, Stack } from "@mui/material";
import { useTranslation } from "react-i18next";

export function FiltersPart({ withSort, isLoading, data = [] }: { data: RowType[]; isLoading?: boolean; withSort?: boolean }): JSX.Element {
    const { t } = useTranslation();
    const { data: userData } = useUserQuery();
    const { data: statusDefinitions = [] } = useStatusDefinitions();

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
        };
    }, [data, filterableKeys, userData?.categories]);

    const statusFilterLabels = statusDefinitions.reduce((map, obj) => {
        map[obj.name] = obj.displayableName;
        return map;
    }, {});
    const { getFilter, setFilter, activeKeys } = useFilterContext<ScenariosFiltersModel>();

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
            <QuickFilter<ScenariosFiltersModel> isLoading={isLoading} filter="NAME">
                <Stack direction="row" spacing={1} p={1} alignItems="center" divider={<Divider orientation="vertical" flexItem />}>
                    <FilterMenu label={t("table.filter.STATUS", "Status")} count={getFilter("STATUS", true).length}>
                        <StatusOptionsStack options={filterableValues["status"]} withArchived={true} />
                    </FilterMenu>
                    <FilterMenu label={t("table.filter.CATEGORY", "Category")} count={getFilter("CATEGORY", true).length}>
                        <SimpleOptionsStack
                            label={t("table.filter.CATEGORY", "Category")}
                            options={filterableValues.processCategory}
                            value={getFilter("CATEGORY", true)}
                            onChange={setFilter("CATEGORY")}
                        />
                    </FilterMenu>
                    <FilterMenu label={t("table.filter.CREATED_BY", "Author")} count={getFilter("CREATED_BY", true).length}>
                        <SimpleOptionsStack
                            label={t("table.filter.CREATED_BY", "Author")}
                            options={filterableValues.author}
                            value={getFilter("CREATED_BY", true)}
                            onChange={setFilter("CREATED_BY")}
                        />
                    </FilterMenu>
                    <FilterMenu label={t("table.filter.other", "Type")} count={getFilter("TYPE", true).length}>
                        <OtherOptionsStack />
                    </FilterMenu>
                    {withSort ? (
                        <FilterMenu label={t("table.filter.SORT_BY", "Sort")}>
                            <SortOptionsStack
                                label={t("table.filter.SORT_BY", "Sort")}
                                options={["createdAt", "modificationDate", "name"].map((name) => ({ name }))}
                                value={getFilter("SORT_BY", true)}
                                onChange={setFilter("SORT_BY")}
                            />
                        </FilterMenu>
                    ) : null}
                </Stack>
            </QuickFilter>
            <ActiveFilters getLabel={getLabel} activeKeys={activeKeys.filter((k) => k !== "NAME" && k !== "SORT_BY")} />
        </>
    );
}
