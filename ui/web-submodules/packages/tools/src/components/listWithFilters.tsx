import { Box, Paper } from "@mui/material";
import { DataGridProps } from "@mui/x-data-grid";
import { flatten, uniq } from "lodash";
import React, { useMemo, useState } from "react";
import { FilterModel, Filters } from "./filters";
import { Table } from "./table";
import { useComponentsQuery } from "./useComponentsQuery";

export type ScenariosTableProps = Pick<DataGridProps, "filterModel" | "onFilterModelChange">;

export function ListWithFilters(): JSX.Element {
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
                <Table filter={filterModel} data={data} isLoading={isLoading} />
            </Box>
        </>
    );
}
