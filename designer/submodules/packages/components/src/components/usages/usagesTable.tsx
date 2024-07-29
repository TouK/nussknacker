import type { ComponentUsageType, NodeUsageData } from "nussknackerUi/HttpService";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ScenarioCell } from "./scenarioCell";
import { Columns, TableViewData, TableWrapper } from "../tableWrapper";
import { createFilterRules, Highlight, useFilterContext } from "../../common";
import { getNodeName, NodesCell } from "./nodesCell";
import { UsagesFiltersModel, UsagesFiltersModelType, UsagesFiltersUsageType } from "./usagesFiltersModel";
import { useDebouncedValue } from "rooks";
import { FilterLinkCell } from "../cellRenderers";
import { UsageWithStatus } from "../useComponentsQuery";
import { Box } from "@mui/material";
import { ScenarioStatusFormatted } from "../cellRenderers/scenarioStatusFormatted";
import { EventTrackingSelector, getEventTrackingProps } from "nussknackerUi/eventTracking";
import { formatDateTime } from "nussknackerUi/DateUtils";

export function nodeFilter(f, u: NodeUsageData) {
    switch (f) {
        case UsagesFiltersUsageType.INDIRECT:
            return u.fragmentNodeId;
        case UsagesFiltersUsageType.DIRECT:
            return !u.fragmentNodeId;
    }
    return false;
}

export function UsagesTable(props: TableViewData<UsageWithStatus>): JSX.Element {
    const { data = [], isLoading } = props;
    const { t } = useTranslation();
    const filtersContext = useFilterContext<UsagesFiltersModel>();
    const _filterText = useMemo(() => filtersContext.getFilter("TEXT"), [filtersContext]);
    const [filterText] = useDebouncedValue(_filterText, 400);

    const columns = useMemo(
        (): Columns<UsageWithStatus> => [
            {
                field: "name",
                cellClassName: "noPadding stretch",
                headerName: t("table.usages.title.NAME", "Name"),
                flex: 3,
                minWidth: 180,
                renderCell: (props) => (
                    <ScenarioCell
                        filterText={filterText}
                        {...getEventTrackingProps({
                            selector: EventTrackingSelector.ScenarioFromComponentUsages,
                        })}
                        {...props}
                    />
                ),
                hideable: false,
            },
            {
                field: "state",
                cellClassName: "stretch",
                headerName: t("table.usages.title.STATUS", "Status"),
                minWidth: 130,
                renderCell: (props) => {
                    if (!props.value) {
                        return (
                            <Box marginY={"auto"} px={1.25}>
                                -
                            </Box>
                        );
                    }

                    return (
                        <FilterLinkCell
                            filterKey="STATUS"
                            {...props}
                            value={props.value.status.name}
                            formattedValue={
                                <ScenarioStatusFormatted
                                    value={props.value.status.name}
                                    icon={props.value.icon}
                                    tooltip={props.value.tooltip}
                                />
                            }
                        />
                    );
                },
            },

            {
                field: "processCategory",
                cellClassName: "stretch",
                headerName: t("table.usages.title.PROCESS_CATEGORY", "Category"),
                renderCell: (props) => <FilterLinkCell<UsagesFiltersModel> filterKey="CATEGORY" {...props} />,
                minWidth: 160,
            },
            {
                field: "createdAt",
                headerName: t("table.usages.title.CREATION_DATE", "Creation date"),
                type: "string",
                flex: 2,
                renderCell: (props) => <Highlight filterText={filterText} {...props} value={formatDateTime(props.value)} />,
                hide: true,
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "createdBy",
                cellClassName: "stretch",
                headerName: t("table.usages.title.CREATED_BY", "Author"),
                renderCell: (props) => <FilterLinkCell<UsagesFiltersModel> filterKey="CREATED_BY" {...props} />,
                hide: true,
                minWidth: 100,
            },
            {
                field: "modifiedBy",
                cellClassName: "stretch",
                headerName: t("table.usages.title.MODIFIED_BY", "Editor"),
                renderCell: (props) => <FilterLinkCell<UsagesFiltersModel> filterKey="CREATED_BY" {...props} />,
                minWidth: 100,
            },
            {
                field: "modificationDate",
                headerName: t("table.usages.title.MODIFICATION_DATE", "Modification date"),
                type: "string",
                renderCell: (props) => <Highlight filterText={filterText} {...props} value={formatDateTime(props.value)} />,
                sortingOrder: ["desc", "asc", null],
                minWidth: 180,
            },
            {
                field: "nodesUsagesData",
                headerName: t("table.usages.title.NODES_ID", "Nodes"),
                minWidth: 230,
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
            createFilterRules<UsageWithStatus, UsagesFiltersModel>({
                CATEGORY: (row, value) => !value?.length || [].concat(value).some((f) => row.processCategory === f),
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
                            .map(({ field }) => {
                                switch (field) {
                                    case "nodesUsagesData":
                                        return row[field]?.map(getNodeName).toString().toLowerCase();
                                    default:
                                        return row[field]?.toString().toLowerCase();
                                }
                            })
                            .filter(Boolean)
                            .some((value) => value.includes(segment.toLowerCase()));
                    });
                },
                TYPE: (row, value) =>
                    !value?.length ||
                    [].concat(value).some((f) => {
                        switch (f) {
                            case UsagesFiltersModelType.SCENARIOS:
                                return !row.isFragment;
                            case UsagesFiltersModelType.FRAGMENTS:
                                return row.isFragment;
                        }
                        return false;
                    }),
                USAGE_TYPE: (row, value) =>
                    !value?.length || [].concat(value).some((f) => row.nodesUsagesData.some((u) => nodeFilter(f, u))),
                STATUS: (row, filter) => !filter?.length || [].concat(filter).some((f) => row.state?.status?.name.includes(f)),
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
            getRowId={(row) => row.name}
            columns={columns}
            data={data}
            isLoading={isLoading}
            filterRules={filterRules}
            columnVisibilityModel={visibleColumns}
            onColumnVisibilityModelChange={setVisibleColumns}
        />
    );
}
