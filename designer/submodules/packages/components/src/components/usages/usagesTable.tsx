import { Box } from "@mui/material";
import type { ProcessStateType } from "nussknackerUi/components/Process/types";
import { formatDateTime } from "nussknackerUi/DateUtils";
import { EventTrackingSelector, getEventTrackingProps } from "nussknackerUi/eventTracking";
import type { ComponentUsageType, NodeUsageData } from "nussknackerUi/HttpService";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDebouncedValue } from "rooks";

import { createFilterRules } from "../../common/filters/filterRules";
import { Highlight } from "../../common/highlight";
import { FilterLinkCell } from "../cellRenderers/filterLinkCell";
import { ScenarioStatusFormatted } from "../cellRenderers/scenarioStatusFormatted";
import { TableWrapper } from "../tableWrapper";
import type { Columns, TableViewData } from "../tableWrapper";
import type { UsageWithStatus } from "../useComponentsQuery";
import { getNodeSearchText, NodesCell } from "./nodesCell";
import { ScenarioCell } from "./scenarioCell";
import { USAGES_FILTER, UsagesFiltersModelType, UsagesFiltersUsageType } from "./usagesFiltersModel";
import type { UsagesFiltersModel } from "./usagesFiltersModel";
import { useUsagesFilterContext } from "./useUsagesFilterContext";

export function nodeFilter(f, u: NodeUsageData) {
    const hasFragmentReference = Boolean(u.fragmentNodeId || u.fragmentNodeName);
    switch (f) {
        case UsagesFiltersUsageType.INDIRECT:
            return hasFragmentReference;
        case UsagesFiltersUsageType.DIRECT:
            return !hasFragmentReference;
    }
    return false;
}

export function UsagesTable(props: TableViewData<UsageWithStatus>): JSX.Element {
    const { data = [], isLoading } = props;
    const { t } = useTranslation();
    const filtersContext = useUsagesFilterContext();
    const _filterText = useMemo(() => filtersContext.getFilter(USAGES_FILTER.TEXT), [filtersContext]);
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
                display: "flex",
                minWidth: 130,
                sortComparator: (s1: ProcessStateType, s2: ProcessStateType) => {
                    // Fragments don't have state so s1 or s2 can be undefined
                    if (!s1 && !s2) return 0;
                    if (!s1) return 1;
                    if (!s2) return -1;

                    return s1.status.name.localeCompare(s2.status.name);
                },
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
                            filterKey={USAGES_FILTER.STATUS}
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
                renderCell: (props) => <FilterLinkCell<UsagesFiltersModel> filterKey={USAGES_FILTER.CATEGORY} {...props} />,
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
                renderCell: (props) => <FilterLinkCell<UsagesFiltersModel> filterKey={USAGES_FILTER.CREATED_BY} {...props} />,
                hide: true,
                minWidth: 100,
            },
            {
                field: "modifiedBy",
                cellClassName: "stretch",
                headerName: t("table.usages.title.MODIFIED_BY", "Editor"),
                renderCell: (props) => <FilterLinkCell<UsagesFiltersModel> filterKey={USAGES_FILTER.CREATED_BY} {...props} />,
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
                                        return row[field]?.map(getNodeSearchText).toString();
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
