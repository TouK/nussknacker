import { Link } from "@mui/material";
import { GridActionsCellItem } from "@mui/x-data-grid";
import { ComponentType } from "nussknackerUi/HttpService";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { CategoriesCell } from "./cellRenderers/categoriesCell";
import { ComponentGroupNameCell } from "./cellRenderers/componentGroupNameCell";
import { NameCell } from "./cellRenderers/nameCell";
import { UsageCountCell } from "./cellRenderers/usageCountCell";
import { FILTER_RULES } from "./filters/filterRules";
import { useFilterContext } from "./filters/filtersContext";
import { Columns, TableViewData, TableWrapper } from "./tableWrapper";
import { IconImg } from "./cellRenderers/iconImg";

export function ComponentTable(props: TableViewData<ComponentType>): JSX.Element {
    const { data = [], isLoading } = props;
    const { getFilter } = useFilterContext();
    const { t } = useTranslation();

    const columns = useMemo(
        (): Columns<ComponentType[]> => [
            {
                field: "name",
                minWidth: 200,
                cellClassName: "noPadding stretch",
                headerName: t("table.title.NAME", "Name"),
                flex: 1,
                renderCell: NameCell,
                sortComparator: (v1, v2) => v1.toString().toLowerCase().localeCompare(v2.toString().toLowerCase()),
            },
            {
                field: "usageCount",
                type: "number",
                cellClassName: "noPadding stretch",
                headerName: t("table.title.USAGE_COUNT", "Uses"),
                renderCell: UsageCountCell,
            },
            {
                field: "componentGroupName",
                cellClassName: "noPadding stretch",
                minWidth: 150,
                headerName: t("table.title.GROUP", "Group"),
                renderCell: ComponentGroupNameCell,
            },
            {
                field: "categories",
                headerName: t("table.title.CATEGORIES", "Categories"),
                minWidth: 250,
                flex: 2,
                sortComparator: (v1: string[], v2: string[]) => v1.length - v2.length,
                renderCell: (props) => <CategoriesCell {...props} filterValue={getFilter("CATEGORY", true)} />,
            },
            {
                field: "links",
                type: "actions",
                getActions: ({ row }) =>
                    row.links.map((link, i) => (
                        <GridActionsCellItem
                            component={Link}
                            key={link.id}
                            href={link.url}
                            icon={<IconImg src={link.icon} title={link.title} />}
                            label={link.title}
                            showInMenu={i > 0}
                            target="_blank"
                        />
                    )),
            },
        ],
        [getFilter, t],
    );

    return <TableWrapper<ComponentType> columns={columns} filterRules={FILTER_RULES} data={data} isLoading={isLoading} />;
}
