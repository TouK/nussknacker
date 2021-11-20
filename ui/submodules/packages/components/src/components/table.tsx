import { Box, Chip, Paper } from "@mui/material";
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
                renderCell: ({ value, row }) => (
                    <>
                        <img title={row.componentType} style={{ height: "1.5em", marginRight: ".25em" }} src={row.icon} />
                        <Highlighter textToHighlight={value.toString()} searchWords={getFilterValue("NAME")} highlightTag={Highlight} />
                    </>
                ),
            },
            {
                field: "usageCount",
                type: "number",
                headerName: t("table.title.USAGE_COUNT", "Uses"),
                renderCell: ({ value }) => <Box sx={{ fontWeight: value ? "bold" : "light" }}>{value}</Box>,
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
                renderCell: ({ row }) => (
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
                            const isSelected = getFilterValue("CATEGORY").includes(name);
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
                ),
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
                    components={{
                        Row: EvenOddRow,
                    }}
                    {...passProps}
                />
            </Box>
        </Box>
    );
}
