import { History } from "@mui/icons-material";
import { Divider, Stack, Typography } from "@mui/material";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { CategoryChip, Highlight } from "../../common";
import { Author } from "./author";
import { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";
import { RowType } from "./listPart";
import { FiltersContextType } from "../../common/filters/filtersContext";

function Category({ value, filtersContext }: { value: string; filtersContext: FiltersContextType<ScenariosFiltersModel> }): JSX.Element {
    const { setFilter, getFilter } = filtersContext;
    const filterValue = useMemo(() => getFilter("CATEGORY", true), [getFilter]);
    return <CategoryChip value={value} filterValue={filterValue} setFilter={setFilter("CATEGORY")} />;
}

export function FirstLine({
    row,
    filtersContext,
}: {
    row: RowType;
    filtersContext: FiltersContextType<ScenariosFiltersModel>;
}): JSX.Element {
    return (
        <Stack direction="row" spacing={1} alignItems="center" divider={<Divider orientation="vertical" flexItem />}>
            <Highlight value={row.id} filterText={filtersContext.getFilter("NAME")} />
            <Category value={row.processCategory} filtersContext={filtersContext} />
            {row.lastAction && (
                <Stack spacing={1} direction="row" alignItems="center">
                    <History />
                    <Typography variant="caption">{row.lastAction.action}</Typography>
                </Stack>
            )}
        </Stack>
    );
}

//TODO: show modifications' date and authors
export function SecondLine({
    row,
    filtersContext,
}: {
    row: RowType;
    filtersContext: FiltersContextType<ScenariosFiltersModel>;
}): JSX.Element {
    const { t } = useTranslation();
    return (
        <span>
            {t("scenario.createdAt", "{{date, relativeDate}}", { date: new Date(row.createdAt) })} by{" "}
            <Author value={row.createdBy} filtersContext={filtersContext} />
        </span>
    );
}
