import { History } from "@mui/icons-material";
import { Divider, Stack, styled, Typography } from "@mui/material";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { CategoryButton, Highlight, useFilterContext, TruncateWrapper } from "../../common";
import { Author } from "./author";
import { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";
import { RowType } from "./listPart";
import { FiltersContextType } from "../../common/filters/filtersContext";
import { CopyTooltip } from "./copyTooltip";
import { ProcessActionType } from "nussknackerUi/components/Process/types";
import { ScenarioStatus } from "./scenarioStatus";
import { ProcessingModeItem } from "./processingMode";
import { formatDateTime } from "nussknackerUi/DateUtils";
import { LabelChip } from "../../common/labelChip";

function Category({
    category,
    filtersContext,
}: {
    category: string;
    filtersContext: FiltersContextType<ScenariosFiltersModel>;
}): JSX.Element {
    const { setFilter, getFilter } = filtersContext;
    const filterValue = useMemo(() => getFilter("CATEGORY", true), [getFilter]);
    return <CategoryButton category={category} filterValues={filterValue} setFilter={setFilter("CATEGORY")} />;
}

function Labels({ values, filtersContext }: { values: string[]; filtersContext: FiltersContextType<ScenariosFiltersModel> }): JSX.Element {
    const { setFilter, getFilter } = filtersContext;
    const filterValue = useMemo(() => getFilter("LABEL", true), [getFilter]);

    const elements = values.map((v) => <LabelChip key={v} id={v} value={v} filterValue={filterValue} setFilter={setFilter("LABEL")} />);
    return <TruncateWrapper>{elements}</TruncateWrapper>;
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
            <Typography variant="caption">{lastAction.actionName}</Typography>
        </Stack>
    ) : null;
}

const HighlightedName = styled(Highlight)({
    fontWeight: "bold",
    fontSize: "1rem",
});

export function FirstLine({ row }: { row: RowType }): JSX.Element {
    const { t } = useTranslation();
    const filtersContext = useFilterContext<ScenariosFiltersModel>();

    return (
        <div style={{ display: "flex" }}>
            <Category category={row.processCategory} filtersContext={filtersContext} />
            <span style={{ paddingLeft: 8, paddingRight: 8 }}>/</span>
            <CopyTooltip text={row.name} title={t("scenario.copyName", "Copy name to clipboard")}>
                <HighlightedName value={row.name} filterText={filtersContext.getFilter("NAME")} />
            </CopyTooltip>
        </div>
    );
}

export function SecondLine({ row }: { row: RowType }): JSX.Element {
    const { getFilter } = useFilterContext<ScenariosFiltersModel>();
    const [createdBy] = getFilter("CREATED_BY", true);
    const [sortedBy] = getFilter("SORT_BY", true);
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
            {!row.isFragment && !row.isArchived && <ScenarioStatus state={row.state} filtersContext={filtersContext} />}
            <ProcessingModeItem processingMode={row.processingMode} filtersContext={filtersContext} />
            {row.labels.length && <Labels values={row.labels} filtersContext={filtersContext} />}
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
            {t("scenario.modifiedAt", "{{date, relativeDate}}", { date: formatDateTime(row.modificationDate) })}{" "}
            {t("scenario.authorBy", "by")} <Author value={row.modifiedBy} />
        </Typography>
    );
}
