import { Box, BoxProps, Paper, useMediaQuery } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import { DataGrid, DataGridProps, GridActionsColDef, GridColDef, GridRenderCellParams } from "@mui/x-data-grid";
import React, { memo, PropsWithChildren, useCallback, useMemo } from "react";
import { CustomPagination } from "./customPagination";
import { FilterRules, useFilterContext } from "../common/filters";
import { useTranslation } from "react-i18next";
import { useDebouncedValue } from "rooks";

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

export type CellRendererParams = GridRenderCellParams;

export function TableWrapper<T, M>(props: TableViewProps<T, M>): JSX.Element {
    const { data = [], filterRules, isLoading, sx, ...passProps } = props;
    const { t } = useTranslation();

    const { getFilter } = useFilterContext<M>();
    const filters = useMemo(
        () =>
            filterRules.map(
                ({ key, rule }) =>
                    (row) =>
                        rule(row, getFilter(key)),
            ),
        [filterRules, getFilter],
    );
    const filtered = useMemo(() => data.filter((row) => filters.every((f) => f(row))), [data, filters]);
    const [rows] = useDebouncedValue(filtered, 100);
    const [loading] = useDebouncedValue(isLoading || rows.length !== filtered.length, 200);

    const rowSelectable = useCallback(() => false, []);
    const localeText = useMemo(
        () => ({
            columnMenuSortAsc: t("table.columnMenu.sort.asc", "Sort by (ASC)"),
            columnMenuSortDesc: t("table.columnMenu.sort.desc", "Sort by (DESC)"),
        }),
        [t],
    );
    const componentsProps = useMemo(
        () => ({
            pagination: {
                allRows: data.length,
            },
            ...passProps.componentsProps,
        }),
        [data.length, passProps.componentsProps],
    );
    const components = useMemo(
        () => ({
            Pagination: CustomPagination,
            ...passProps.components,
        }),
        [passProps.components],
    );
    return (
        <Layout>
            <DataGrid
                isRowSelectable={rowSelectable}
                autoPageSize
                rows={rows}
                loading={loading}
                localeText={localeText}
                disableColumnFilter
                disableSelectionOnClick
                {...passProps}
                componentsProps={componentsProps}
                components={components}
            />
        </Layout>
    );
}

const Layout = memo(function Layout({ children }: PropsWithChildren<unknown>) {
    const theme = useTheme();
    const md = useMediaQuery(theme.breakpoints.up("md"));

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
                {children}
            </Box>
        </Box>
    );
});
