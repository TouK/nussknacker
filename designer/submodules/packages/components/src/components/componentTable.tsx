import { ComponentType } from "nussknackerUi/HttpService";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { CategoriesCell, FilterLinkCell, NameCell, UsageCountCell } from "./cellRenderers";
import { Columns, TableViewData, TableWrapper } from "./tableWrapper";
import { ExternalLink, NuIcon } from "../common";
import { filterRules } from "./filterRules";
import { ComponentsFiltersModel } from "./filters";
import { GridActionsCellItem } from "@mui/x-data-grid";
import { useNavigate } from "react-router-dom";

export function ComponentTable(props: TableViewData<ComponentType>): JSX.Element {
    const navigate = useNavigate();
    const { data = [], isLoading } = props;
    const { t } = useTranslation();

    const columns = useMemo(
        (): Columns<ComponentType> => [
            {
                field: "name",
                minWidth: 200,
                cellClassName: "noPadding stretch",
                headerName: t("table.title.NAME", "Name"),
                flex: 1,
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
                minWidth: 150,
                headerName: t("table.title.GROUP", "Group"),
                renderCell: (props) => <FilterLinkCell<ComponentsFiltersModel> filterKey="GROUP" {...props} />,
            },
            {
                field: "categories",
                headerName: t("table.title.CATEGORIES", "Categories"),
                minWidth: 250,
                flex: 2,
                sortComparator: (v1: string[], v2: string[]) => v1.length - v2.length,
                renderCell: (props) => <CategoriesCell {...props} />,
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "links",
                type: "actions",
                getActions: ({ row }) =>
                    row.links.map((link, i) => (
                        <GridActionsCellItem
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
