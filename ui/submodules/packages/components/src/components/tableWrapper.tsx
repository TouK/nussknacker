import { Box, BoxProps, Paper, useMediaQuery } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import { DataGrid, DataGridProps, GridActionsColDef, GridColDef, GridRenderCellParams } from "@mui/x-data-grid";
import React, { useMemo } from "react";
import { CustomPagination } from "./customPagination";
import { FilterRules, useFilterContext } from "../common/filters";
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

interface TableViewProps<T, M> extends TableViewData<T>, Pick<BoxProps, "sx"> {
    columns: Columns<T[]>;
    filterRules?: FilterRules<T, M>;
}

export function TableWrapper<T, M>(props: TableViewProps<T, M>): JSX.Element {
    const { data = [], filterRules, isLoading, sx, ...passProps } = props;
    const theme = useTheme();
    const md = useMediaQuery(theme.breakpoints.up("md"));
    const { t } = useTranslation();

    const { getFilter } = useFilterContext<M>();

    const filtered = useMemo(() => {
        return data.filter((row) => filterRules.every(({ key, rule }) => rule(row, getFilter(key))));
    }, [data, filterRules, getFilter]);

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
