import React, { useCallback, useMemo } from "react";
import { flatten, uniq, uniqBy } from "lodash";
import { useFilterContext } from "../../common";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";
import { useUserQuery } from "../useScenariosQuery";
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

    const filterableKeys = useMemo(() => ["createdBy", "modifiedBy"], []);
    const filterableValues = useMemo(() => {
        const entries = filterableKeys.map((k) => [k, uniq(flatten(data.map((v) => v[k]))).sort()]);
        return {
            ...Object.fromEntries(entries),
            author: uniq(["modifiedBy", "createdBy"].flatMap((k) => data.flatMap((v) => v[k])))
                .sort()
                .map((v) => ({ name: v })),
            status: uniqBy(
                data.map((v) => ({ name: v.state?.status.name, icon: v.state?.icon })),
                "name",
            ).sort(),
            processCategory: (userData?.categories || []).map((name) => ({ name })),
        };
    }, [data, filterableKeys, userData?.categories]);

    const statusFilters: Array<keyof ScenariosFiltersModel> = ["HIDE_DEPLOYED", "HIDE_NOT_DEPLOYED", "HIDE_ACTIVE", "SHOW_ARCHIVED"];
    const { getFilter, setFilter, activeKeys } = useFilterContext<ScenariosFiltersModel>();

    const otherFilters: Array<keyof ScenariosFiltersModel> = ["HIDE_SCENARIOS", "HIDE_FRAGMENTS"];

    const getLabel = useCallback(
        (name: keyof ScenariosFiltersModel, value?: string | number) => {
            switch (name) {
                case "HIDE_ACTIVE":
                    return t("table.filter.desc.HIDE_ACTIVE", "Active hidden");
                case "HIDE_FRAGMENTS":
                    return t("table.filter.desc.HIDE_FRAGMENTS", "Fragments hidden");
                case "HIDE_SCENARIOS":
                    return t("table.filter.desc.HIDE_SCENARIOS", "Scenarios hidden");
                case "SHOW_ARCHIVED":
                    return t("table.filter.desc.SHOW_ARCHIVED", "Archived visible");
                case "HIDE_DEPLOYED":
                    return t("table.filter.desc.HIDE_DEPLOYED", "Deployed hidden");
                case "HIDE_NOT_DEPLOYED":
                    return t("table.filter.desc.HIDE_NOT_DEPLOYED", "Not deployed hidden");
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
            <QuickFilter<ScenariosFiltersModel> isLoading={isLoading} filter="NAME">
                <Stack direction="row" spacing={1} p={1} alignItems="center" divider={<Divider orientation="vertical" flexItem />}>
                    {/*<FilterMenu label={t("table.filter.STATUS", "Status")} count={getFilter("STATUS", true).length}>*/}
                    {/*    <SimpleOptionsStack*/}
                    {/*        label={t("table.filter.STATUS", "Status")}*/}
                    {/*        options={filterableValues["status"]}*/}
                    {/*        value={getFilter("STATUS", true)}*/}
                    {/*        onChange={setFilter("STATUS")}*/}
                    {/*    />*/}
                    {/*</FilterMenu>*/}
                    <FilterMenu label={t("table.filter.STATUS", "Status")} count={statusFilters.filter((k) => getFilter(k)).length}>
                        <StatusOptionsStack />
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
                    <FilterMenu label={t("table.filter.other", "Type")} count={otherFilters.filter((k) => getFilter(k)).length}>
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
