import { Box, BoxProps, Paper, useMediaQuery } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import {
    DataGrid,
    DataGridProps,
    GridActionsColDef,
    GridColDef,
    GridRenderCellParams,
    GridSlotsComponentsProps,
    useGridApiRef,
} from "@mui/x-data-grid";
import React, { memo, PropsWithChildren, useCallback, useMemo } from "react";
import { CustomPagination } from "./customPagination";
import { FilterRules, useFilterContext } from "../common/filters";
import { useTranslation } from "react-i18next";
import { useDebouncedValue } from "rooks";

export type CellRendererParams<R = any, K = unknown> = GridRenderCellParams<K extends keyof R ? R[K] : any, R>;

export type ColumnDef<R = unknown, K = unknown> = GridColDef & {
    field?: K;
    renderCell?: (params: CellRendererParams<R, K>) => React.ReactNode;
    hide?: boolean;
};
export type Column<R> = ColumnDef<R, keyof R> | ColumnDef<R, string> | (GridActionsColDef & { hide?: boolean });
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
    const apiRef = useGridApiRef();
    const { data = [], filterRules, isLoading, ...passProps } = props;
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
    const slotProps: GridSlotsComponentsProps & { pagination: { allRows?: number } } = useMemo(
        () => ({
            pagination: {
                allRows: data.length,
            },
            ...passProps.slotProps,
        }),
        [data.length, passProps.slotProps],
    );
    const slots = useMemo(
        () => ({
            pagination: CustomPagination,
            ...passProps.slots,
        }),
        [passProps.slots],
    );

    return (
        <Layout>
            <DataGrid
                apiRef={apiRef}
                isRowSelectable={rowSelectable}
                autoPageSize
                rows={rows}
                getRowHeight={() => 50} // In the case of a default 52px row height, there is a problem with correctly resizing the table when the window height changes.
                loading={loading}
                localeText={localeText}
                disableColumnFilter
                disableRowSelectionOnClick
                {...passProps}
                slotProps={slotProps}
                slots={slots}
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
