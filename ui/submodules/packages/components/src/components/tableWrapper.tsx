import { Box, BoxProps, Paper, useMediaQuery } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import { DataGrid, DataGridProps, GridActionsColDef, GridColDef } from "@mui/x-data-grid";
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

export interface TableViewData<T> extends Partial<DataGridProps> {
    data: T[];
    isLoading?: boolean;
}

interface TableViewProps<T> extends TableViewData<T>, Pick<BoxProps, "sx"> {
    columns: Columns<T[]>;
    filterRules?: FilterRules<T>;
}

export function TableWrapper<T>(props: TableViewProps<T>): JSX.Element {
    const { data = [], filterRules, isLoading, sx, ...passProps } = props;
    const theme = useTheme();
    const md = useMediaQuery(theme.breakpoints.up("md"));

    const { model } = useFilterContext();
    const dataFilter = useCallback(
        (row) =>
            !filterRules ||
            Object.keys(filterRules).every((id) => {
                const check = filterRules[id];
                const value = model[id];
                return check ? check(row, value) : true;
            }),
        [filterRules, model],
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
                    {...passProps}
                    componentsProps={{
                        pagination: {
                            allRows: data.length,
                        },
                        ...passProps.componentsProps,
                    }}
                    components={{
                        Pagination: CustomPagination,
                        ...passProps.components,
                    }}
                />
            </Box>
        </Box>
    );
}
