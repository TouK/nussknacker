import { Box, Paper } from "@mui/material";
import { DataGrid, GridActionsColDef, GridColDef } from "@mui/x-data-grid";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { ScenariosTableProps } from "./listWithFilters";
import type { ComponentType } from "nussknackerUi/HttpService";
import { NameCell } from "./cellRenderers/nameCell";
import { UsageCountCell } from "./cellRenderers/usageCountCell";
import { CategoriesCell } from "./cellRenderers/categoriesCell";
import { FILTER_RULES } from "./filters/filterRules";
import { useFilterContext } from "./filters/filtersContext";
import { ComponentGroupNameCell } from "./cellRenderers/componentGroupNameCell";

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
}

export function Table(props: ScenariosTableViewProps): JSX.Element {
    const { data = [], isLoading, ...passProps } = props;
    const { model, getFilter } = useFilterContext();

    const { t } = useTranslation();

    const columns = useMemo(
        (): Columns<typeof data> => [
            {
                field: "name",
                minWidth: 200,
                headerName: t("table.title.NAME", "Name"),
                flex: 1,
                renderCell: (props) => <NameCell {...props} filterValue={getFilter("NAME", true)} />,
                sortComparator: (v1, v2) => v1.toString().toLowerCase().localeCompare(v2.toString().toLowerCase()),
            },
            {
                field: "usageCount",
                type: "number",
                cellClassName: "noPadding stretch",
                headerName: t("table.title.USAGE_COUNT", "Uses"),
                renderCell: UsageCountCell,
            },
            {
                field: "componentGroupName",
                cellClassName: "noPadding stretch",
                minWidth: 150,
                headerName: t("table.title.GROUP", "Group"),
                renderCell: ComponentGroupNameCell,
            },
            {
                field: "categories",
                headerName: t("table.title.CATEGORIES", "Categories"),
                minWidth: 150,
                flex: 2,
                sortable: false,
                renderCell: (props) => <CategoriesCell {...props} filterValue={getFilter("CATEGORY", true)} />,
            },
        ],
        [getFilter, t],
    );

    const filtered = useMemo(() => {
        return data.filter((row) => {
            return Object.entries(model).every(([id, value]) => {
                const check = FILTER_RULES[id];
                return value && check ? check(row, value) : true;
            });
        });
    }, [data, model]);

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
