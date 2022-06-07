import React, { useCallback, useMemo } from "react";
import { useParams } from "react-router-dom";
import { UsagesTable } from "./usagesTable";
import { useComponentUsagesQuery } from "../useComponentsQuery";
import { FiltersContextProvider, useFilterContext } from "../../common";
import { Breadcrumbs } from "./breadcrumbs";
import { UsagesFiltersModel } from "./usagesFiltersModel";
import { ActiveFilters } from "../../scenarios/filters/activeFilters";
import { uniq } from "lodash";
import { useUserQuery } from "../../scenarios/useScenariosQuery";
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
                case "HIDE_DEPLOYED":
                    return value && setNewValue("HIDE_NOT_DEPLOYED", false);
                case "HIDE_NOT_DEPLOYED":
                    return value && setNewValue("HIDE_DEPLOYED", false);
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
    const { data = [], isLoading } = useComponentUsagesQuery(componentId);
    const { t } = useTranslation();

    const { data: userData } = useUserQuery();
    const filterableValues = useMemo(() => {
        const entries = ["createdBy"].map((k) => [
            k,
            uniq(data.flatMap((v) => v[k]))
                .sort()
                .map((v) => ({ name: v })),
        ]);
        return Object.fromEntries([...entries, ["categories", (userData?.categories || []).map((name) => ({ name }))]]);
    }, [data, userData?.categories]);

    const { activeKeys } = useFilterContext<UsagesFiltersModel>();
    const getLabel = useCallback(
        (name: keyof UsagesFiltersModel, value?: string | number) => {
            switch (name) {
                case "HIDE_FRAGMENTS":
                    return t("table.filter.desc.HIDE_FRAGMENTS", "Fragments hidden");
                case "HIDE_SCENARIOS":
                    return t("table.filter.desc.HIDE_SCENARIOS", "Scenarios hidden");
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
            <Breadcrumbs />
            <FiltersPart isLoading={isLoading} filterableValues={filterableValues} />
            <ActiveFilters<UsagesFiltersModel> getLabel={getLabel} activeKeys={activeKeys.filter((k) => k !== "TEXT")} />
            <UsagesTable data={data} isLoading={isLoading} />
        </>
    );
}
