import { Box, Paper } from "@mui/material";
import { flatten, uniq } from "lodash";
import React, { useCallback, useMemo } from "react";
import { ComponentTable } from "./componentTable";
import { Filters } from "./filters";
import { useComponentsQuery } from "./useComponentsQuery";
import { FiltersContextProvider } from "../common/filters";
import { ComponentsFiltersModel } from "./filters/componentsFiltersModel";
import { ValueLinker } from "../common/filters/filtersContext";

export function ListWithFilters(): JSX.Element {
    const { data = [], isLoading } = useComponentsQuery();

    const filterableKeys = useMemo(() => uniq(flatten(data.map((v) => Object.keys(v)))), [data]);
    const filterableValues = useMemo(
        () => Object.fromEntries(filterableKeys.map((k) => [k, uniq(flatten(data.map((v) => v[k]))).sort()])),
        [data, filterableKeys],
    );

    const valueLinker: ValueLinker<ComponentsFiltersModel> = useCallback(
        (setNewValue) => (id, value) => {
            switch (id) {
                case "USED_ONLY":
                    return value && setNewValue("UNUSED_ONLY", false);
                case "UNUSED_ONLY":
                    return value && setNewValue("USED_ONLY", false);
            }
        },
        [],
    );

    return (
        <FiltersContextProvider<ComponentsFiltersModel> getValueLinker={valueLinker}>
            <Box component={Paper} display="flex" p={2}>
                <Filters values={filterableValues} />
            </Box>
            <ComponentTable data={data} isLoading={isLoading} />
        </FiltersContextProvider>
    );
}
