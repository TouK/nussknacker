import type { ComponentUsageType } from "nussknackerUi/HttpService";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { CustomCell } from "./cellRenderers/categoriesCell";
import { ScenarioCell } from "./cellRenderers/scenarioCell";
import { Columns, TableViewData, TableWrapper } from "./tableWrapper";
import { FilterRules } from "./filters/filterRules";
import Highlighter from "react-highlight-words";
import { Highlight } from "./cellRenderers/nameCell";
import { useFilterContext } from "./filters/filtersContext";

export function Highlighted({ value }: { value: string }): JSX.Element {
    const { getFilter } = useFilterContext();
    return <Highlighter autoEscape textToHighlight={value} searchWords={getFilter("TEXT", true)} highlightTag={Highlight} />;
}

export function UsagesTable(props: TableViewData<ComponentUsageType>): JSX.Element {
    const { data = [], isLoading } = props;
    const { t } = useTranslation();

    const columns = useMemo(
        (): Columns<ComponentUsageType[]> => [
            {
                field: "name",
                cellClassName: "noPadding stretch",
                headerName: t("table.usages.title.NAME", "Name"),
                flex: 2,
                renderCell: ScenarioCell,
            },
            {
                field: "isSubprocess",
                headerName: t("table.usages.title.IS_FRAGMENT", "Fragment?"),
                type: "boolean",
            },
            {
                field: "processCategory",
                headerName: t("table.usages.title.PROCESS_CATEGORY", "Process category"),
                renderCell: Highlighted,
            },
            {
                field: "modificationDate",
                headerName: t("table.usages.title.MODIFICATION_DATE", "Modification date"),
                type: "dateTime",
                flex: 1,
                renderCell: Highlighted,
            },
            {
                field: "createdAt",
                headerName: t("table.usages.title.CREATION_DATE", "Creation date"),
                type: "dateTime",
                flex: 1,
                renderCell: Highlighted,
            },
            {
                field: "createdBy",
                headerName: t("table.usages.title.CREATED_BY", "Created by"),
                renderCell: Highlighted,
            },
            {
                field: "nodesId",
                headerName: t("table.usages.title.NODES_ID", "Nodes"),
                minWidth: 250,
                flex: 2,
                sortComparator: (v1: string[], v2: string[]) => v1.length - v2.length,
                renderCell: CustomCell,
            },
        ],
        [t],
    );

    const filterRules = useMemo<FilterRules<ComponentUsageType>>(
        () => ({
            TEXT: (row, filter) =>
                !filter?.length ||
                columns
                    .map(({ field }) => row[field]?.toString().toLowerCase())
                    .filter(Boolean)
                    .some((value) => value.includes(filter.toLowerCase())),
            SHOW_ARCHIVED: (row, filter) => filter || !row.isArchived,
        }),
        [columns],
    );

    return (
        <TableWrapper<ComponentUsageType>
            sx={{
                ".archived": {
                    color: "warning.primary",
                },
            }}
            getRowClassName={(p) => (p.row.isArchived ? "archived" : "")}
            columns={columns}
            data={data}
            isLoading={isLoading}
            filterRules={filterRules}
        />
    );
}
