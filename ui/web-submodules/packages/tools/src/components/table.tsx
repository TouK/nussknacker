import { Box, Chip, Paper, useTheme } from "@mui/material";
import { DataGrid, GridColDef, GridRow, GridRowProps } from "@mui/x-data-grid";
import React, { PropsWithChildren, useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { FilterModel } from "./filters";
import { ScenariosTableProps } from "./listWithFilters";
import type { ComponentType } from "nussknackerUi/HttpService";
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

const Highlight = ({ children }: PropsWithChildren<unknown>) => (
    <Box component="span" sx={{ color: "primary.main" }}>
        {children}
    </Box>
);

const EvenOddRow = (props: GridRowProps) => <GridRow className={props.index % 2 ? "even" : "odd"} {...props} />;

export function Table(props: ScenariosTableViewProps): JSX.Element {
    const { data = [], isLoading, filter = [], ...passProps } = props;
    const { t } = useTranslation();

    const getFilterValue = useCallback((field) => filter.find(({ id }) => id === field)?.value.toString() || null, [filter]);

    const columns = useMemo(
        (): Columns<typeof data> => [
            {
                field: "name",
                minWidth: 200,
                headerName: t("table.title.NAME", "Name"),
                flex: 1,
                renderCell: ({ value }) => {
                    const filterValue = getFilterValue("name");
                    return <Highlighter textToHighlight={value.toString()} searchWords={[filterValue]} highlightTag={Highlight} />;
                },
            },
            {
                field: "componentType",
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
                field: "usageCount",
                type: "number",
                headerName: t("table.title.USAGE_COUNT", "Usage count"),
                renderCell: ({ value }) => (
                    <>
                        <Box sx={{ fontWeight: value ? "bold" : "light" }}>{value}</Box>
                    </>
                ),
            },
            {
                field: "componentGroupName",
                headerName: t("table.title.GROUP_NAME", "Group"),
            },
            {
                field: "categories",
                headerName: t("table.title.CATEGORIES", "Categories"),
                minWidth: 150,
                flex: 2,
                sortable: false,
                renderCell: ({ row }) => {
                    const filterValue = getFilterValue("categories");
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
                    disableSelectionOnClick
                    components={{
                        Row: EvenOddRow,
                    }}
                    {...passProps}
                />
            </Box>
        </Box>
    );
}
