import { Box, Chip, Paper } from "@mui/material";
import { DataGrid, GridColDef } from "@mui/x-data-grid";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { FilterModel } from "../filters";
import { ScenariosTableProps } from "./scenariosTable";
import { ComponentType } from "./useProcessesQuery";
import Highlighter from "react-highlight-words";
type ArrayElement<ArrayType extends readonly unknown[]> = ArrayType extends readonly (infer ElementType)[] ? ElementType : never;

type Columns<R extends Array<unknown>> = Array<
    GridColDef & {
        field: keyof ArrayElement<R> | string;
    }
>;

export interface ScenariosTableViewProps extends ScenariosTableProps {
    data: ComponentType[];
    isLoading?: boolean;
    filter?: FilterModel;
}

export function ScenariosTableView(props: ScenariosTableViewProps): JSX.Element {
    const { data = [], isLoading, filter = [], ...passProps } = props;
    const { t } = useTranslation();

    const getFilter = useCallback((field) => filter.find(({ column }) => column === field)?.value || null, [filter]);

    const columns = useMemo(
        (): Columns<typeof data> => [
            {
                field: "id",
                minWidth: 200,
                headerName: t("table.title.NAME", "Name"),
                flex: 1,
                // renderCell: ({ value, field }) => {
                //     const filterValue = getFilter(field);
                //     return <Highlighter textToHighlight={value.toString()} searchWords={[filterValue]} />;
                // },
            },
            {
                field: "type",
                minWidth: 150,
                headerName: t("table.title.TYPE", "Type"),
                renderCell: ({ row, value }) => (
                    <>
                        <img style={{ height: "1.5em", marginRight: ".25em" }} src={row.icon} />
                        {value}
                    </>
                ),
            },
            {
                field: "categories",
                headerName: t("table.title.CATEGORIES", "Categories"),
                minWidth: 150,
                flex: 2,
                sortable: false,
                renderCell: ({ field, row }) => {
                    const filterValue = getFilter(field);
                    return (
                        <Box
                            sx={{
                                flex: 1,
                                overflow: "hidden",
                                display: "flex",
                                gap: 0.5,
                                position: "relative",
                                maskImage: "linear-gradient(to right, rgba(0, 0, 0, 1) 90%, rgba(0, 0, 0, 0))",
                            }}
                        >
                            {row.categories.map((name) => {
                                const isSelected = filterValue?.includes(name);
                                return (
                                    <Chip
                                        key={name}
                                        label={name}
                                        size="small"
                                        variant={isSelected ? "outlined" : "filled"}
                                        color={isSelected ? "primary" : "default"}
                                    />
                                );
                            })}
                        </Box>
                    );
                },
            },
            {
                field: "isUsed",
                headerName: t("table.title.IS_USED", "Is used?"),
                valueFormatter: ({ value }) => (value ? "✅" : "❌"),
            },
            {
                field: "service",
                headerName: t("table.title.IS_SERVICE", "Is service?"),
                valueFormatter: ({ value }) => (value ? "✅" : "❌"),
            },
        ],
        [getFilter, t],
    );

    const filtered = useMemo(() => {
        return data.filter((row) => {
            return filter.every((f) => {
                const rawValue = row[f.column];
                const filters = f.value.length ? f.value.split("|") : [];
                return !filters.length || filters.some((f) => rawValue.includes(f));
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
                    disableSelectionOnClick
                    {...passProps}
                />
            </Box>
        </Box>
    );
}
