import type { ComponentUsageType } from "nussknackerUi/HttpService";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { ScenarioCell } from "./cellRenderers/scenarioCell";
import { Columns, TableViewData, TableWrapper } from "./tableWrapper";
import { FilterRules } from "./filters/filterRules";
import Highlighter from "react-highlight-words";
import { Highlight } from "./cellRenderers/nameCell";
import { useFilterContext } from "./filters/filtersContext";
import { RocketLaunch, Pause } from "@mui/icons-material";
import { NodesCell } from "./cellRenderers/nodesCell";

export function Highlighted({ value }: { value: string }): JSX.Element {
    const { getFilter } = useFilterContext();
    const [filter] = getFilter("TEXT", true);
    return <Highlighter autoEscape textToHighlight={value.toString()} searchWords={[`${filter}`]}
                        highlightTag={Highlight} />;
}

const isDeployed = (r: ComponentUsageType): boolean => r.lastAction ? r.lastAction.action === "DEPLOY" : null;

export function UsagesTable(props: TableViewData<ComponentUsageType>): JSX.Element {
    const { data = [], isLoading } = props;
    const { t } = useTranslation();

    const columns = useMemo(
        (): Columns<ComponentUsageType[]> => [
            {
                field: "name",
                cellClassName: "noPadding stretch",
                headerName: t("table.usages.title.NAME", "Name"),
                flex: 3,
                minWidth: 160,
                renderCell: ScenarioCell,
                hideable: false,
            },
            {
                field: "isFragment",
                headerName: t("table.usages.title.IS_FRAGMENT", "Fragment"),
                valueGetter: ({ row }) => row.isSubprocess,
                type: "boolean",
            },
            {
                field: "processCategory",
                headerName: t("table.usages.title.PROCESS_CATEGORY", "Category"),
                renderCell: Highlighted,
                flex: 1,
            },
            {
                field: "createdAt",
                headerName: t("table.usages.title.CREATION_DATE", "Creation date"),
                type: "dateTime",
                flex: 2,
                renderCell: Highlighted,
                hide: true,
            },
            {
                field: "createdBy",
                headerName: t("table.usages.title.CREATED_BY", "Author"),
                renderCell: Highlighted,
                flex: 1,
            },
            {
                field: "modificationDate",
                headerName: t("table.usages.title.MODIFICATION_DATE", "Modification date"),
                type: "dateTime",
                flex: 2,
                renderCell: Highlighted,
            },
            {
                field: "isDeployed",
                headerName: t("table.usages.title.IS_DEPLOYED", "Deployed"),
                valueGetter: ({ row }) => isDeployed(row),
                type: "boolean",
                renderCell: ({ value }) => {
                    if (value === null) {
                        return <></>;
                    }
                    if (!value) {
                        return <Pause color="disabled" />;
                    }
                    return <RocketLaunch color="warning" />;
                },
            },
            {
                field: "nodesId",
                headerName: t("table.usages.title.NODES_ID", "Nodes"),
                minWidth: 250,
                flex: 4,
                sortComparator: (v1: string[], v2: string[]) => v1.length - v2.length,
                renderCell: NodesCell,
                hideable: false,
            },
        ],
        [t],
    );

    const filterRules = useMemo<FilterRules<ComponentUsageType>>(
        () => ({
            TEXT: (row, filter) =>
                !filter?.toString().length ||
                columns
                    .map(({ field }) => row[field]?.toString().toLowerCase())
                    .filter(Boolean)
                    .some((value) => value.includes(filter.toString().toLowerCase())),
            SHOW_ARCHIVED: (row, filter) => filter || !row.isArchived,
            CATEGORY: (row, value) => !value?.length || [].concat(value).some((f) => row["processCategory"]?.includes(f)),
            CREATED_BY: (row, value) => !value?.length || [].concat(value).some((f) => row["createdBy"]?.includes(f)),
            DEPLOYED_ONLY: (row, value) => (value ? isDeployed(row) : true),
            NOT_DEPLOYED_ONLY: (row, value) => (value ? !isDeployed(row) : true),
            FRAGMENTS_ONLY: (row, value) => (value ? row.isSubprocess : true),
            NOT_FRAGMENTS_ONLY: (row, value) => (value ? !row.isSubprocess : true),
        }),
        [columns],
    );

    return (
        <TableWrapper<ComponentUsageType>
            sx={{
                ".archived": {
                    color: "warning.main",
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
