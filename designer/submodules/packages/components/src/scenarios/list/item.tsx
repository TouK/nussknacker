import { History } from "@mui/icons-material";
import { Divider, Stack, Typography } from "@mui/material";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { CategoryChip, Highlight, useFilterContext } from "../../common";
import { Author } from "./author";
import { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";
import { RowType } from "./listPart";
import { FiltersContextType } from "../../common/filters/filtersContext";
import { CopyTooltip } from "./copyTooltip";
import { ProcessActionType } from "nussknackerUi/components/Process/types";
import { ScenarioStatus } from "./scenarioStatus";

function Category({ value, filtersContext }: { value: string; filtersContext: FiltersContextType<ScenariosFiltersModel> }): JSX.Element {
    const { setFilter, getFilter } = filtersContext;
    const filterValue = useMemo(() => getFilter("CATEGORY", true), [getFilter]);
    return <CategoryChip value={value} filterValue={filterValue} setFilter={setFilter("CATEGORY")} />;
}

export function LastAction({ lastAction }: { lastAction: ProcessActionType }): JSX.Element {
    const { t } = useTranslation();

    return lastAction ? (
        <Stack
            spacing={1}
            direction="row"
            alignItems="center"
            title={t("scenario.lastAction", "Last action performed {{date, relativeDate}}.", {
                date: new Date(lastAction.performedAt),
            })}
        >
            <History />
            <Typography variant="caption">{lastAction.actionType}</Typography>
        </Stack>
    ) : null;
}

export function FirstLine({ row }: { row: RowType }): JSX.Element {
    const { t } = useTranslation();
    const filtersContext = useFilterContext<ScenariosFiltersModel>();

    return (
        <CopyTooltip text={row.name} title={t("scenario.copyName", "Copy name to clipboard")}>
            <Highlight value={row.name} filterText={filtersContext.getFilter("NAME")} />
        </CopyTooltip>
    );
}

export function SecondLine({ row }: { row: RowType }): JSX.Element {
    const { getFilter } = useFilterContext<ScenariosFiltersModel>();
    const [createdBy] = getFilter("CREATED_BY", true);
    const [sortedBy] = getFilter("SORT_BY", true);
    const sortedByCreation = !sortedBy || sortedBy.startsWith("createdAt");
    const filteredByCreation = createdBy === row.createdBy;
    const filtersContext = useFilterContext<ScenariosFiltersModel>();

    return (
        <Stack
            direction="row"
            spacing={1.25}
            mt={1}
            alignItems="center"
            divider={<Divider orientation="vertical" variant={"inset"} flexItem />}
        >
            <div>
                <ModificationDate row={row} />
                {filteredByCreation ? (
                    <>
                        {" "}
                        (<CreationDate row={row} />)
                    </>
                ) : null}
            </div>
            {!row.isFragment && !row.isArchived && <ScenarioStatus state={row.state} />}
            <Category value={row.processCategory} filtersContext={filtersContext} />
        </Stack>
    );
}

function CreationDate({ row }: { row: RowType }): JSX.Element {
    const { t } = useTranslation();
    return (
        <Typography variant={"caption"}>
            {t("scenario.createdAt", "created {{date, relativeDate}}", { date: new Date(row.createdAt) })} {t("scenario.authorBy", "by")}{" "}
            <Author value={row.createdBy} />
        </Typography>
    );
}

function ModificationDate({ row }: { row: RowType }): JSX.Element {
    const { t } = useTranslation();

    return (
        <Typography variant={"caption"}>
            {t("scenario.modifiedAtLabel", "Last modified:")}{" "}
            {t("scenario.modifiedAt", "{{date, relativeDate}}", { date: row.modificationDate })} {t("scenario.authorBy", "by")}{" "}
            <Author value={row.modifiedBy} />
        </Typography>
    );
}
