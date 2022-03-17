import { History } from "@mui/icons-material";
import { Divider, Stack, Typography } from "@mui/material";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { CategoryChip, Highlight, useFilterContext } from "../../common";
import { Author } from "./author";
import { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";
import Highlighter from "react-highlight-words";
import { RowType } from "./listPart";

function Highlighted({ value }: { value: string }): JSX.Element {
    const { getFilter } = useFilterContext<ScenariosFiltersModel>();
    return (
        <Highlighter
            autoEscape
            textToHighlight={value.toString()}
            searchWords={getFilter("NAME")?.toString().split(/\s/) || []}
            highlightTag={Highlight}
        />
    );
}

function Category({ value }: { value: string }): JSX.Element {
    const { setFilter, getFilter } = useFilterContext<ScenariosFiltersModel>();
    const filterValue = useMemo(() => getFilter("CATEGORY", true), [getFilter]);
    return <CategoryChip value={value} filterValue={filterValue} setFilter={setFilter("CATEGORY")} />;
}

export function FirstLine({ row }: { row: RowType }): JSX.Element {
    return (
        <Stack direction="row" spacing={1} alignItems="center" divider={<Divider orientation="vertical" flexItem />}>
            <Highlighted value={row.id} />
            <Category value={row.processCategory} />
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
export function SecondLine({ row }: { row: RowType }): JSX.Element {
    const { t } = useTranslation();
    return (
        <span>
            {t("scenario.createdAt", "{{date, relativeDate}}", { date: new Date(row.createdAt) })} by <Author value={row.createdBy} />
        </span>
    );
}
