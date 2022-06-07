import type { ComponentUsageType } from "nussknackerUi/HttpService";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { ScenarioCell } from "./scenarioCell";
import { Columns, TableViewData, TableWrapper } from "../tableWrapper";
import { createFilterRules, Highlight, useFilterContext } from "../../common";
import { Pause, RocketLaunch } from "@mui/icons-material";
import { NodesCell } from "./nodesCell";
import { UsagesFiltersModel } from "./usagesFiltersModel";
import { useDebouncedValue } from "rooks";
import { ScenarioAuthorCell } from "../cellRenderers/scenarioAuthorCell";

const isDeployed = (r: ComponentUsageType): boolean => (r.lastAction ? r.lastAction.action === "DEPLOY" : null);

export function UsagesTable(props: TableViewData<ComponentUsageType>): JSX.Element {
    const { data = [], isLoading } = props;
    const { t } = useTranslation();
    const filtersContext = useFilterContext<UsagesFiltersModel>();
    const _filterText = useMemo(() => filtersContext.getFilter("TEXT"), [filtersContext]);
    const [filterText] = useDebouncedValue(_filterText, 400);

    const columns = useMemo(
        (): Columns<ComponentUsageType> => [
            {
                field: "name",
                cellClassName: "noPadding stretch",
                headerName: t("table.usages.title.NAME", "Name"),
                flex: 3,
                minWidth: 160,
                renderCell: (props) => <ScenarioCell filterText={filterText} {...props} />,
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
                renderCell: (props) => <Highlight filterText={filterText} {...props} />,
                flex: 1,
            },
            {
                field: "createdAt",
                headerName: t("table.usages.title.CREATION_DATE", "Creation date"),
                type: "dateTime",
                flex: 2,
                renderCell: (props) => <Highlight filterText={filterText} {...props} />,
                hide: true,
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "createdBy",
                cellClassName: "noPadding stretch",
                headerName: t("table.usages.title.CREATED_BY", "Author"),
                renderCell: (props) => <ScenarioAuthorCell {...props} />,
                flex: 1,
            },
            {
                field: "modificationDate",
                headerName: t("table.usages.title.MODIFICATION_DATE", "Modification date"),
                type: "dateTime",
                flex: 2,
                renderCell: (props) => <Highlight filterText={filterText} {...props} />,
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
                renderCell: (props) => <NodesCell filterText={filterText} {...props} />,
                hideable: false,
                sortingOrder: ["desc", "asc", null],
            },
        ],
        [filterText, t],
    );

    const filterRules = useMemo(
        () =>
            createFilterRules<ComponentUsageType, UsagesFiltersModel>({
                CATEGORY: (row, value) => !value?.length || [].concat(value).some((f) => row["processCategory"] === f),
                CREATED_BY: (row, value) => !value?.length || [].concat(value).some((f) => row["createdBy"]?.includes(f)),
                TEXT: (row, filter) => {
                    const text = filter?.toString();
                    if (!text?.length) return true;
                    const segments = text.trim().split(/\s/);
                    return segments.every((segment) =>
                        columns
                            .filter((value) => !value.hide)
                            .filter((value) => value.field !== "processCategory" && value.field !== "createdBy")
                            .map(({ field }) => row[field]?.toString().toLowerCase())
                            .filter(Boolean)
                            .some((value) => value.includes(segment.toLowerCase())),
                    );
                },
                HIDE_FRAGMENTS: (row, filter) => (filter ? !row.isSubprocess : true),
                HIDE_SCENARIOS: (row, filter) => (filter ? row.isSubprocess : true),
                HIDE_DEPLOYED: (row, filter) => (filter ? row.lastAction?.action !== "DEPLOY" : true),
                HIDE_NOT_DEPLOYED: (row, filter) => (filter ? row.lastAction?.action === "DEPLOY" : true),
            }),
        [columns],
    );

    const rowClassName = useCallback((p) => (p.row.isArchived ? "archived" : ""), []);
    const sx = useMemo(
        () => ({
            ".archived": {
                color: "warning.main",
            },
        }),
        [],
    );
    return (
        <TableWrapper<ComponentUsageType, UsagesFiltersModel>
            sx={sx}
            getRowClassName={rowClassName}
            columns={columns}
            data={data}
            isLoading={isLoading}
            filterRules={filterRules}
        />
    );
}
