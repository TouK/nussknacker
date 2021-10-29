import { Box, Paper } from "@mui/material";
import { DataGridProps } from "@mui/x-data-grid";
import { flatten, flattenDeep, uniq } from "lodash";
import React, { useEffect, useMemo, useState } from "react";
import { FilterModel, Filters } from "../filters";
import { ScenariosTableView } from "./scenariosTableView";
import { useComponentsQuery } from "./useProcessesQuery";

export type ScenariosTableProps = Pick<DataGridProps, "filterModel" | "onFilterModelChange">;

export function ScenariosTable(): JSX.Element {
    const [filterModel, setFilterModel] = useState<FilterModel>([]);

    const { data = [], isLoading } = useComponentsQuery();

    const filterableKeys = useMemo(() => uniq(flatten(data.map((v) => Object.keys(v)))), [data]);
    const filterableValues = useMemo(() => {
        return Object.fromEntries(filterableKeys.map((k) => [k, uniq(flatten(data.map((v) => v[k])))]));
    }, [data, filterableKeys]);

    return (
        <>
            <Box component={Paper} display="flex" p={3}>
                <Filters model={filterModel} onFilterChange={setFilterModel} values={filterableValues} />
            </Box>
            <Box display="flex" overflow="hidden" flex={3}>
                <ScenariosTableView filter={filterModel} data={data} isLoading={isLoading} />
            </Box>
        </>
    );
}
