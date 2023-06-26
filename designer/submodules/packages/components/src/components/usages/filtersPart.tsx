import { UsagesFiltersModel, UsagesFiltersValues } from "./usagesFiltersModel";
import { useFilterContext } from "../../common";
import { QuickFilter } from "../../scenarios/filters/quickFilter";
import { FilterMenu } from "../../scenarios/filters/filterMenu";
import { SimpleOptionsStack } from "../../scenarios/filters/simpleOptionsStack";
import { StatusOptionsStack } from "../../scenarios/filters/otherOptionsStack";
import { OptionsStack } from "../../scenarios/filters/optionsStack";
import { FilterListItem } from "../../scenarios/filters/filterListItem";
import React from "react";
import { useTranslation } from "react-i18next";
import { Divider, Stack } from "@mui/material";

interface FiltersPartProps {
    isLoading: boolean;
    filterableValues: UsagesFiltersValues;
}

export function FiltersPart({ isLoading, filterableValues }: FiltersPartProps): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<UsagesFiltersModel>();

    return (
        <QuickFilter<UsagesFiltersModel> isLoading={isLoading} filter="TEXT">
            <Stack direction="row" spacing={1} p={1} alignItems="center" divider={<Divider orientation="vertical" flexItem />}>
                <FilterMenu label={t("table.filter.STATUS", "Status")} count={getFilter("STATUS", true).length}>
                    <StatusOptionsStack options={filterableValues["STATUS"]} withArchived={false} />
                </FilterMenu>
                <FilterMenu label={t("table.filter.CATEGORY", "Category")} count={getFilter("CATEGORY", true).length}>
                    <SimpleOptionsStack
                        label={t("table.filter.CATEGORY", "Category")}
                        options={filterableValues["CATEGORY"]}
                        value={getFilter("CATEGORY", true)}
                        onChange={setFilter("CATEGORY")}
                    />
                </FilterMenu>
                <FilterMenu label={t("table.filter.AUTHOR", "Author")} count={getFilter("CREATED_BY", true).length}>
                    <SimpleOptionsStack
                        label={t("table.filter.AUTHOR", "Author")}
                        options={filterableValues["CREATED_BY"]}
                        value={getFilter("CREATED_BY", true)}
                        onChange={setFilter("CREATED_BY")}
                    />
                </FilterMenu>
            </Stack>
        </QuickFilter>
    );
}
