import { Box, Paper, useMediaQuery, useTheme } from "@mui/material";
import { DataGrid, GridActionsColDef, GridColDef } from "@mui/x-data-grid";
import React, { useCallback, useMemo } from "react";
import { CustomPagination } from "./customPagination";
import { FilterRules } from "./filters/filterRules";
import { useFilterContext } from "./filters/filtersContext";

type ArrayElement<ArrayType extends readonly unknown[]> = ArrayType extends readonly (infer ElementType)[] ? ElementType : never;
type ColumnDef<R> = GridColDef & {
    field?: keyof R | string;
};
export type Column<R> = ColumnDef<R> | GridActionsColDef;
export type Columns<R extends Array<unknown>> = Array<Column<ArrayElement<R>>>;

export interface TableViewData<T> {
    data: T[];
    isLoading?: boolean;
}

interface TableViewProps<T> extends TableViewData<T> {
    columns: Columns<T[]>;
    filterRules?: FilterRules<T>;
}

export function TableWrapper<T>(props: TableViewProps<T>): JSX.Element {
    const { data = [], filterRules, isLoading, ...passProps } = props;
    const theme = useTheme();
    const md = useMediaQuery(theme.breakpoints.up("md"));

    const { model } = useFilterContext();
    const dataFilter = useCallback(
        (row) =>
            !filterRules ||
            Object.entries(model).every(([id, value]) => {
                const check = filterRules[id];
                return value && check ? check(row, value) : true;
            }),
        [model, passProps.columns],
    );
    const filtered = useMemo(() => (dataFilter ? data.filter(dataFilter) : data), [data, dataFilter]);

    return (
        <Box
            sx={{
                flexDirection: "column",
                display: "flex",
                width: "100%",
                flex: 1,
                minHeight: md ? "50vh" : "80vh",
            }}
        >
            <Box sx={{ display: "flex", width: "100%", flex: 1 }} component={Paper}>
                <DataGrid
                    isRowSelectable={() => false}
                    autoPageSize
                    rows={filtered}
                    loading={isLoading}
                    disableColumnFilter
                    disableColumnSelector
                    disableColumnMenu
                    disableSelectionOnClick
                    componentsProps={{
                        pagination: {
                            allRows: data.length,
                        },
                    }}
                    components={{
                        Pagination: CustomPagination,
                    }}
                    {...passProps}
                />
            </Box>
        </Box>
    );
}
