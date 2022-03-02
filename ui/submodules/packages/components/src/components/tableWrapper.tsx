import { Box, BoxProps, Paper, useMediaQuery } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import { DataGrid, DataGridProps, GridActionsColDef, GridColDef, GridRenderCellParams } from "@mui/x-data-grid";
import React, { useCallback, useMemo } from "react";
import { CustomPagination } from "./customPagination";
import { FilterRules } from "./filters/filterRules";
import { useFilterContext } from "./filters/filtersContext";
import { useTranslation } from "react-i18next";

type ColumnDef<R, K = unknown> = GridColDef & {
    field?: K;
    renderCell?: (params: GridRenderCellParams<K extends keyof R ? R[K] : never, R>) => React.ReactNode;
};
export type Column<R> = ColumnDef<R, keyof R> | ColumnDef<R, string> | GridActionsColDef;
export type Columns<R> = Column<R>[];

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
    const { t } = useTranslation();

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
                minHeight: md ? "50vh" : "180vh",
            }}
        >
            <Box sx={{ display: "flex", width: "100%", flex: 1, overflow: "auto" }} component={Paper}>
                <DataGrid
                    isRowSelectable={() => false}
                    autoPageSize
                    rows={filtered}
                    loading={isLoading}
                    localeText={{
                        columnMenuSortAsc: t("table.columnMenu.sort.asc", "Sort by (ASC)"),
                        columnMenuSortDesc: t("table.columnMenu.sort.desc", "Sort by (DESC)"),
                    }}
                    disableColumnFilter
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
