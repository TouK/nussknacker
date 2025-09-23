import type { GridColDef } from "@mui/x-data-grid";
import { GridActionsCellItem } from "@mui/x-data-grid";
import type { ComponentType } from "nussknackerUi/HttpService";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { NuIcon } from "../common/nuIcon";
import { ExternalLink } from "../common/parentNavigationProvider";
import { CategoriesCell } from "./cellRenderers/categoriesCell";
import { FilterLinkCell } from "./cellRenderers/filterLinkCell";
import { NameCell } from "./cellRenderers/nameCell";
import { ProcessingModesCell } from "./cellRenderers/processingModesCell";
import { UsageCountCell } from "./cellRenderers/usageCountCell";
import { filterRules } from "./filterRules";
import type { ComponentsFiltersModel } from "./filters/componentsFiltersModel";
import type { TableViewData } from "./tableWrapper";
import { Columns, TableWrapper } from "./tableWrapper";

export function ComponentTable(props: TableViewData<ComponentType>): JSX.Element {
    const navigate = useNavigate();
    const { data = [], isLoading } = props;
    const { t } = useTranslation();

    const columns: (GridColDef & { display?: string })[] = useMemo(
        (): (GridColDef & { display?: string })[] => [
            {
                field: "name",
                minWidth: 200,
                cellClassName: "noPadding stretch",
                headerName: t("table.title.NAME", "Name"),
                flex: 4,
                display: "flex",
                renderCell: (props) => <NameCell {...props} />,
                sortComparator: (v1, v2) => v1.toString().toLowerCase().localeCompare(v2.toString().toLowerCase()),
                hideable: false,
            },
            {
                field: "usageCount",
                type: "number",
                cellClassName: "noPadding stretch",
                headerName: t("table.title.USAGE_COUNT", "Usages"),
                renderCell: (props) => <UsageCountCell {...props} />,
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "componentGroupName",
                cellClassName: "noPadding stretch",
                display: "flex",
                minWidth: 150,
                headerName: t("table.title.GROUP", "Group"),
                renderCell: (props) => <FilterLinkCell<ComponentsFiltersModel> filterKey="GROUP" {...props} />,
            },
            {
                field: "categories",
                headerName: t("table.title.CATEGORIES", "Categories"),
                minWidth: 350,
                flex: 2,
                display: "flex",
                sortComparator: (v1: string[], v2: string[]) => v1.length - v2.length,
                renderCell: (props) => <CategoriesCell {...props} />,
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "allowedProcessingModes",
                headerName: t("table.title.PROCESSING_MODE", "Processing modes"),
                minWidth: 500,
                display: "flex",
                flex: 4,
                sortComparator: (v1: string[], v2: string[]) => v1.length - v2.length,
                renderCell: (props) => <ProcessingModesCell {...props} />,
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "links",
                type: "actions",
                display: "flex",
                getActions: ({ row }) =>
                    row.links.map((link, i) => (
                        <GridActionsCellItem
                            color={"inherit"}
                            component={ExternalLink}
                            key={link.id}
                            icon={<NuIcon src={link.icon} title={link.title} sx={{ fontSize: "1.5rem", verticalAlign: "middle" }} />}
                            label={link.title}
                            showInMenu={i > 0}
                            onKeyDown={(e) => {
                                const keycode = e.keyCode ? e.keyCode : e.which;
                                const ENTER = "13";
                                if (keycode == ENTER) {
                                    // Open the link using keyboard navigation and press enter to click on the menu item.
                                    navigate(link.url);
                                }
                            }}
                            // From @mui/x-data-grid:5.10.0 version it's a problem with a type definition, but we are able to pass component prop
                            // eslint-disable-next-line @typescript-eslint/ban-ts-comment
                            // @ts-ignore
                            href={link.url}
                            target="_blank"
                        ></GridActionsCellItem>
                    )),
            },
        ],
        [navigate, t],
    );

    return (
        <TableWrapper<ComponentType, ComponentsFiltersModel>
            columns={columns}
            filterRules={filterRules}
            data={data}
            isLoading={isLoading}
        />
    );
}
