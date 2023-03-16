import type { ComponentUsageType } from "nussknackerUi/HttpService";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ScenarioCell } from "./scenarioCell";
import { Columns, TableViewData, TableWrapper } from "../tableWrapper";
import { createFilterRules, Highlight, useFilterContext } from "../../common";
import { NodesCell } from "./nodesCell";
import { UsagesFiltersModel } from "./usagesFiltersModel";
import { useDebouncedValue } from "rooks";
import { FilterLinkCell } from "../cellRenderers";

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
                field: "processCategory",
                cellClassName: "noPadding stretch",
                headerName: t("table.usages.title.PROCESS_CATEGORY", "Category"),
                renderCell: (props) => <FilterLinkCell<UsagesFiltersModel> filterKey="CATEGORY" {...props} />,
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
                renderCell: (props) => <FilterLinkCell<UsagesFiltersModel> filterKey="CREATED_BY" {...props} />,
                hide: true,
                flex: 1,
            },
            {
                field: "modifiedBy",
                cellClassName: "noPadding stretch",
                headerName: t("table.usages.title.MODIFIED_BY", "Editor"),
                renderCell: (props) => <FilterLinkCell<UsagesFiltersModel> filterKey="CREATED_BY" {...props} />,
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

    const [visibleColumns, setVisibleColumns] = useState(
        columns.reduce((previousValue, currentValue) => {
            return { ...previousValue, [currentValue.field]: !currentValue.hide };
        }, {}),
    );

    const filterRules = useMemo(
        () =>
            createFilterRules<ComponentUsageType, UsagesFiltersModel>({
                CATEGORY: (row, value) => !value?.length || [].concat(value).some((f) => row["processCategory"] === f),
                CREATED_BY: (row, value) =>
                    !value?.length ||
                    [].concat(value).some((f) =>
                        columns
                            .filter((value) => visibleColumns[value.field])
                            .filter((value) => ["createdBy", "modifiedBy"].includes(value.field))
                            .map(({ field }) => row[field])
                            .filter(Boolean)
                            .some((value) => value.includes(f)),
                    ),
                TEXT: (row, filter) => {
                    const text = filter?.toString();
                    if (!text?.length) return true;
                    const segments = text.trim().split(/\s/);
                    return segments.every((segment) => {
                        return columns
                            .filter((value) => visibleColumns[value.field])
                            .filter((value) => !["processCategory", "createdBy", "modifiedBy"].includes(value.field))
                            .map(({ field }) => row[field]?.toString().toLowerCase())
                            .filter(Boolean)
                            .some((value) => value.includes(segment.toLowerCase()));
                    });
                },
                HIDE_FRAGMENTS: (row, filter) => (filter ? !row.isSubprocess : true),
                HIDE_SCENARIOS: (row, filter) => (filter ? row.isSubprocess : true),
            }),
        [columns, visibleColumns],
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
            columnVisibilityModel={visibleColumns}
            onColumnVisibilityModelChange={setVisibleColumns}
        />
    );
}
