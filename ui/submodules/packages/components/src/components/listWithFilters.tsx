import { Box, Paper } from "@mui/material";
import { DataGridProps } from "@mui/x-data-grid";
import { flatten, uniq } from "lodash";
import React, { useMemo } from "react";
import { Filters } from "./filters/filters";
import { Table } from "./table";
import { useComponentsQuery } from "./useComponentsQuery";
import { FiltersContextProvider } from "./filters/filtersContext";

export type ScenariosTableProps = Pick<DataGridProps, "filterModel" | "onFilterModelChange">;

export function ListWithFilters(): JSX.Element {
    const { data = [], isLoading } = useComponentsQuery();

    const filterableKeys = useMemo(() => uniq(flatten(data.map((v) => Object.keys(v)))), [data]);
    const filterableValues = useMemo(
        () => Object.fromEntries(filterableKeys.map((k) => [k, uniq(flatten(data.map((v) => v[k]))).sort()])),
        [data, filterableKeys],
    );

    return (
        <FiltersContextProvider>
            <Box component={Paper} display="flex" p={3}>
                <Filters values={filterableValues} />
            </Box>
            <Box display="flex" overflow="hidden" flex={3}>
                <Table data={data} isLoading={isLoading} />
            </Box>
        </FiltersContextProvider>
    );
}
