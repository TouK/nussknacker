import React, { useCallback, useMemo, useState } from "react";
import { Columns, FilterLinkCell, TableWrapper } from "../../components";
import { ScenariosFiltersModel, ScenariosFiltersModelType } from "../filters/scenariosFiltersModel";
import { ListPartProps, RowType } from "./listPart";
import { useTranslation } from "react-i18next";
import { createFilterRules, ExternalLink, Highlight, metricsHref, useFilterContext } from "../../common";
import { ScenarioCell } from "../../components/usages/scenarioCell";
import { useDebouncedValue } from "rooks";
import { IconButton } from "@mui/material";
import AssessmentIcon from "@mui/icons-material/Assessment";
import { LastAction } from "./item";
import { getEventTrackingProps, EventTrackingSelector } from "nussknackerUi/eventTracking";
import { formatDateTime } from "nussknackerUi/DateUtils";
import { useScenariosWithCategoryVisible } from "../useScenariosQuery";

export function TablePart(props: ListPartProps<RowType>): JSX.Element {
    const { data = [], isLoading } = props;
    const { t } = useTranslation();
    const filtersContext = useFilterContext<ScenariosFiltersModel>();
    const _filterText = useMemo(() => filtersContext.getFilter("NAME"), [filtersContext]);
    const [filterText] = useDebouncedValue(_filterText, 400);
    const { withCategoriesVisible } = useScenariosWithCategoryVisible();

    const columns = useMemo((): Columns<RowType> => {
        const availableColumns: Columns<RowType | undefined> = [
            {
                field: "id",
                cellClassName: "noPadding stretch",
                headerName: t("table.scenarios.title.NAME", "Name"),
                renderCell: (props) => <ScenarioCell filterText={filterText} {...props} />,
                sortComparator: (v1, v2) => v1.toString().toLowerCase().localeCompare(v2.toString().toLowerCase()),
                hideable: false,
                minWidth: 200,
                flex: 2,
            },
            withCategoriesVisible
                ? {
                      field: "processCategory",
                      cellClassName: "noPadding stretch",
                      headerName: t("table.scenarios.title.PROCESS_CATEGORY", "Category"),
                      renderCell: (props) => <FilterLinkCell<ScenariosFiltersModel> filterKey="CATEGORY" {...props} />,
                      flex: 1,
                  }
                : undefined,
            {
                field: "createdBy",
                cellClassName: "noPadding stretch",
                headerName: t("table.scenarios.title.CREATED_BY", "Author"),
                renderCell: (props) => <FilterLinkCell<ScenariosFiltersModel> filterKey="CREATED_BY" {...props} />,
                hide: true,
                flex: 1,
            },
            {
                field: "createdAt",
                headerName: t("table.scenarios.title.CREATION_DATE", "Creation date"),
                type: "string",
                flex: 2,
                renderCell: (props) => <Highlight filterText={filterText} {...props} />,
                hide: true,
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "modifiedBy",
                cellClassName: "noPadding stretch",
                headerName: t("table.scenarios.title.MODIFIED_BY", "Editor"),
                renderCell: (props) => <FilterLinkCell<ScenariosFiltersModel> filterKey="CREATED_BY" {...props} />,
                flex: 1,
            },
            {
                field: "labels",
                cellClassName: "stretch",
                headerName: t("table.scenarios.title.LABEL", "Labels"),
                renderCell: (props) => <FilterLinkCell<ScenariosFiltersModel> filterKey="LABEL" {...props} />,
                flex: 1,
            },
            {
                field: "modificationDate",
                headerName: t("table.scenarios.title.MODIFICATION_DATE", "Modification date"),
                type: "string",
                flex: 2,
                renderCell: (props) => <Highlight filterText={filterText} {...props} value={formatDateTime(props.value)} />,
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "lastAction",
                headerName: t("table.scenarios.title.LAST_ACTION", "Last action"),
                renderCell: (props) => <LastAction lastAction={props.value} />,
                sortComparator: (v1, v2) =>
                    (v1?.["action"] || "")
                        .toString()
                        .toLowerCase()
                        .localeCompare((v2?.["action"] || "").toString().toLowerCase()),
                flex: 1,
            },
            {
                field: "metrics",
                headerName: t("table.scenarios.title.METRICS", "Metrics"),
                renderCell: ({ row }) =>
                    !row.isFragment ? (
                        <IconButton
                            color={"inherit"}
                            component={ExternalLink}
                            href={metricsHref(row.name)}
                            {...getEventTrackingProps({
                                selector: EventTrackingSelector.ActionMetrics,
                            })}
                        >
                            <AssessmentIcon />
                        </IconButton>
                    ) : null,
                sortable: false,
                align: "center",
            },
        ];

        return availableColumns.filter((data) => data !== undefined);
    }, [filterText, t, withCategoriesVisible]);

    const [visibleColumns, setVisibleColumns] = useState(
        columns.reduce((previousValue, currentValue) => {
            return { ...previousValue, [currentValue.field]: !currentValue.hide };
        }, {}),
    );

    const filterRules = useMemo(
        () =>
            createFilterRules<RowType, ScenariosFiltersModel>({
                NAME: (row, filter) => {
                    const text = filter?.toString();
                    if (!text?.length) return true;
                    const segments = text.trim().split(/\s/);
                    return segments.every((segment) => {
                        return columns
                            .filter((value) => visibleColumns[value.field])
                            .filter((value) => ["id", "createdAt", "modificationDate"].includes(value.field))
                            .map(({ field }) => row[field]?.toString().toLowerCase())
                            .filter(Boolean)
                            .some((value) => value.includes(segment.toLowerCase()));
                    });
                },
                ARCHIVED: (row, filter) => (filter ? row.isArchived : !row.isArchived),
                TYPE: (row, value) =>
                    !value?.length ||
                    []
                        .concat(value)
                        .some(
                            (f) =>
                                (f === ScenariosFiltersModelType.SCENARIOS && !row.isFragment) ||
                                (f === ScenariosFiltersModelType.FRAGMENTS && row.isFragment),
                        ),
                CATEGORY: (row, value) => !value?.length || [].concat(value).some((f) => row["processCategory"] === f),
                LABEL: (row, value) => !value?.length || [].concat(value).some((f) => row["labels"] === f),
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
                STATUS: (row, value) => !value?.length || [].concat(value).some((f) => row["state"]?.status.name.includes(f)),
            }),
        [columns, visibleColumns],
    );

    const rowClassName = useCallback((p) => (p.row.isArchived ? "archived" : ""), []);
    const sx = useMemo(
        () => ({
            ".archived": {
                color: "text.disabled",
            },
        }),
        [],
    );

    return (
        <TableWrapper<RowType, ScenariosFiltersModel>
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
