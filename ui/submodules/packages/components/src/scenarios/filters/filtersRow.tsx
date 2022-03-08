import React, { useMemo } from "react";
import { flatten, uniq, uniqBy } from "lodash";
import { useFilterContext } from "../../common";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";
import { useUserQuery } from "../useScenariosQuery";
import { QuickFilter } from "./quickFilter";
import { FilterMenu } from "./filterMenu";
import { SimpleOptionsStack } from "./simpleOptionsStack";
import { OtherOptionsStack } from "./otherOptionsStack";
import { SortOptionsStack } from "./sortOptionsStack";
import { ActiveFilters } from "./activeFilters";
import { RowType, TableViewData } from "../tableView";
import { Divider, Stack } from "@mui/material";
import { useTranslation } from "react-i18next";

export function FiltersRow(props: TableViewData<RowType>): JSX.Element {
    const { data = [], isLoading } = props;

    const filterableKeys = useMemo(() => ["isArchived", "isSubprocess", "processCategory", "createdBy"], []);
    const filterableValues = useMemo(() => {
        const entries = filterableKeys.map((k) => [k, uniq(flatten(data.map((v) => v[k]))).sort()]);
        return Object.fromEntries([
            ...entries,
            [
                "status",
                uniqBy(
                    data.map((v) => ({ name: v.state?.status.name, icon: v.state?.icon })),
                    "name",
                ).sort(),
            ],
        ]);
    }, [data, filterableKeys]);

    const { t } = useTranslation();

    const { getFilter, setFilter } = useFilterContext<ScenariosFiltersModel>();

    const otherFilters = ["HIDE_SCENARIOS", "HIDE_FRAGMENTS", "HIDE_ACTIVE", "SHOW_ARCHIVED"];

    const { data: userData } = useUserQuery();
    const categories = (userData?.categories || filterableValues["processCategory"]).map((name) => ({ name }));

    return (
        <>
            <QuickFilter>
                <Stack direction="row" spacing={1} p={1} alignItems="center" divider={<Divider orientation="vertical" flexItem />}>
                    <FilterMenu label={t("table.filter.STATUS", "Status")} count={getFilter("STATUS", true).length}>
                        <SimpleOptionsStack
                            label={t("table.filter.STATUS", "Status")}
                            options={filterableValues["status"]}
                            value={getFilter("STATUS", true)}
                            onChange={setFilter("STATUS")}
                        />
                    </FilterMenu>
                    <FilterMenu label={t("table.filter.CATEGORY", "Category")} count={getFilter("CATEGORY", true).length}>
                        <SimpleOptionsStack
                            label={t("table.filter.CATEGORY", "Category")}
                            options={categories}
                            value={getFilter("CATEGORY", true)}
                            onChange={setFilter("CATEGORY")}
                        />
                    </FilterMenu>
                    <FilterMenu label={t("table.filter.CREATED_BY", "Author")} count={getFilter("CREATED_BY", true).length}>
                        <SimpleOptionsStack
                            label={t("table.filter.CREATED_BY", "Author")}
                            options={filterableValues["createdBy"].map((name) => ({ name }))}
                            value={getFilter("CREATED_BY", true)}
                            onChange={setFilter("CREATED_BY")}
                        />
                    </FilterMenu>
                    <FilterMenu
                        label={t("table.filter.other", "Other")}
                        count={otherFilters.filter((k: keyof ScenariosFiltersModel) => getFilter(k)).length}
                    >
                        <OtherOptionsStack />
                    </FilterMenu>
                    <FilterMenu label={t("table.filter.SORT_BY", "Sort")}>
                        <SortOptionsStack
                            label={t("table.filter.SORT_BY", "Sort")}
                            options={["createdAt", "modificationDate", "name"].map((name) => ({ name }))}
                            value={getFilter("SORT_BY", true)}
                            onChange={setFilter("SORT_BY")}
                        />
                    </FilterMenu>
                </Stack>
            </QuickFilter>
            <ActiveFilters />
        </>
    );
}
