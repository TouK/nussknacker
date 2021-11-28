import { Box, Paper } from "@mui/material";
import { DataGrid, GridActionsColDef, GridColDef } from "@mui/x-data-grid";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { FilterModel } from "./filters";
import { ScenariosTableProps } from "./listWithFilters";
import type { ComponentType } from "nussknackerUi/HttpService";
import { NameCell } from "./cellRenderers/nameCell";
import { UsageCountCell } from "./cellRenderers/usageCountCell";
import { CategoriesCell } from "./cellRenderers/categoriesCell";

type ArrayElement<ArrayType extends readonly unknown[]> = ArrayType extends readonly (infer ElementType)[] ? ElementType : never;

type Columns<R extends Array<unknown>> = Array<
    | (GridColDef & {
          field?: keyof ArrayElement<R> | string;
      })
    | GridActionsColDef
>;

export interface ScenariosTableViewProps extends ScenariosTableProps {
    data: ComponentType[];
    isLoading?: boolean;
    filter?: FilterModel;
}

export function Table(props: ScenariosTableViewProps): JSX.Element {
    const { data = [], isLoading, filter = [], ...passProps } = props;
    const { t } = useTranslation();

    const getFilterValue = useCallback(
        (field: string) => {
            const value = filter.find(({ id }) => id === field)?.value;
            return value ? [].concat(value) : [];
        },
        [filter],
    );

    const columns = useMemo(
        (): Columns<typeof data> => [
            {
                field: "name",
                minWidth: 200,
                headerName: t("table.title.NAME", "Name"),
                flex: 1,
                renderCell: (props) => <NameCell {...props} filterValue={getFilterValue("NAME")} />,
                sortComparator: (v1, v2) => v1.toString().toLowerCase().localeCompare(v2.toString().toLowerCase()),
            },
            {
                field: "usageCount",
                type: "number",
                cellClassName: "withLink",
                headerName: t("table.title.USAGE_COUNT", "Uses"),
                renderCell: UsageCountCell,
            },
            {
                field: "componentGroupName",
                minWidth: 150,
                headerName: t("table.title.GROUP", "Group"),
            },
            {
                field: "categories",
                headerName: t("table.title.CATEGORIES", "Categories"),
                minWidth: 150,
                flex: 2,
                sortable: false,
                renderCell: (props) => <CategoriesCell {...props} filterValue={getFilterValue("CATEGORY")} />,
            },
        ],
        [getFilterValue, t],
    );

    const filtered = useMemo(() => {
        return data.filter((row) => {
            return filter.every(({ check, value }) => {
                return value && check ? check(row, value) : true;
            });
        });
    }, [data, filter]);

    return (
        <Box flexDirection="column" display="flex" width="100%" flex={1}>
            <Box display="flex" width="100%" flex={1} component={Paper}>
                <DataGrid
                    isRowSelectable={() => false}
                    autoPageSize
                    columns={columns}
                    rows={filtered}
                    loading={isLoading}
                    disableColumnFilter
                    disableColumnSelector
                    disableColumnMenu
                    disableSelectionOnClick
                    {...passProps}
                />
            </Box>
        </Box>
    );
}
