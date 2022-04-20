import type { ComponentUsageType } from "nussknackerUi/HttpService";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { ScenarioCell } from "./scenarioCell";
import { CellRendererParams, Columns, TableViewData, TableWrapper } from "../tableWrapper";
import { createFilterRules, useFilterContext } from "../../common";
import { Pause, RocketLaunch } from "@mui/icons-material";
import { NodesCell } from "./nodesCell";
import { UsagesFiltersModel } from "./usagesFiltersModel";
import Highlighter from "react-highlight-words";
import { Highlight } from "../utils";

function Highlighted({ filtersContext, value }: CellRendererParams<UsagesFiltersModel>): JSX.Element {
    const { getFilter } = filtersContext;
    return (
        <Highlighter
            autoEscape
            textToHighlight={value.toString()}
            searchWords={getFilter("TEXT")?.toString().split(/\s/) || []}
            highlightTag={Highlight}
        />
    );
}

const isDeployed = (r: ComponentUsageType): boolean => (r.lastAction ? r.lastAction.action === "DEPLOY" : null);

export function UsagesTable(props: TableViewData<ComponentUsageType>): JSX.Element {
    const { data = [], isLoading } = props;
    const { t } = useTranslation();
    const filtersContext = useFilterContext<UsagesFiltersModel>();

    const columns = useMemo(
        (): Columns<ComponentUsageType> => [
            {
                field: "name",
                cellClassName: "noPadding stretch",
                headerName: t("table.usages.title.NAME", "Name"),
                flex: 3,
                minWidth: 160,
                renderCell: (props) => <ScenarioCell filtersContext={filtersContext} {...props} />,
                hideable: false,
            },
            {
                field: "isFragment",
                headerName: t("table.usages.title.IS_FRAGMENT", "Fragment"),
                valueGetter: ({ row }) => row.isSubprocess,
                type: "boolean",
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "processCategory",
                headerName: t("table.usages.title.PROCESS_CATEGORY", "Category"),
                renderCell: (props) => <Highlighted filtersContext={filtersContext} {...props} />,
                flex: 1,
            },
            {
                field: "createdAt",
                headerName: t("table.usages.title.CREATION_DATE", "Creation date"),
                type: "dateTime",
                flex: 2,
                renderCell: (props) => <Highlighted filtersContext={filtersContext} {...props} />,
                hide: true,
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "createdBy",
                headerName: t("table.usages.title.CREATED_BY", "Author"),
                renderCell: (props) => <Highlighted filtersContext={filtersContext} {...props} />,
                flex: 1,
            },
            {
                field: "modificationDate",
                headerName: t("table.usages.title.MODIFICATION_DATE", "Modification date"),
                type: "dateTime",
                flex: 2,
                renderCell: (props) => <Highlighted filtersContext={filtersContext} {...props} />,
                sortingOrder: ["desc", "asc", null],
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
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "nodesId",
                headerName: t("table.usages.title.NODES_ID", "Nodes"),
                minWidth: 250,
                flex: 4,
                sortComparator: (v1: string[], v2: string[]) => v1.length - v2.length,
                renderCell: (props) => <NodesCell filtersContext={filtersContext} {...props} />,
                hideable: false,
                sortingOrder: ["desc", "asc", null],
            },
        ],
        [filtersContext, t],
    );

    const filterRules = useMemo(
        () =>
            createFilterRules<ComponentUsageType, UsagesFiltersModel>({
                TEXT: (row, filter) =>
                    !filter?.toString().length ||
                    columns
                        .map(({ field }) => row[field]?.toString().toLowerCase())
                        .filter(Boolean)
                        .some((value) => value.includes(filter.toString().toLowerCase())),
            }),
        [columns],
    );

    return (
        <TableWrapper<ComponentUsageType, UsagesFiltersModel>
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
