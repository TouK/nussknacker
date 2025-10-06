import { Divider, Stack, styled, Typography } from "@mui/material";
import { formatDateTime } from "nussknackerUi/DateUtils";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { CategoryButton } from "../../common/categoryChip";
import type { FiltersContextType } from "../../common/filters/filtersContext";
import { Highlight } from "../../common/highlight";
import { LabelChip } from "../../common/labelChip";
import { TruncateWrapper } from "../../common/utils/truncateWrapper";
import { useScenariosFilterContext } from "../filters/common/useScenariosFilterContext";
import type { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";
import { useScenariosWithCategoryVisible } from "../useScenariosQuery";
import { Author } from "./author";
import { CopyTooltip } from "./copyTooltip";
import type { RowType } from "./listPart";
import { ProcessingModeItem } from "./processingMode";
import { ScenarioStatus } from "./scenarioStatus";

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

const HighlightedName = styled(Highlight)({
    fontWeight: "bold",
    fontSize: "1rem",
});

export function FirstLine({ row }: { row: RowType }): JSX.Element {
    const { t } = useTranslation();
    const filtersContext = useScenariosFilterContext();
    const { withCategoriesVisible } = useScenariosWithCategoryVisible();

    return (
        <div style={{ display: "flex" }}>
            {withCategoriesVisible && (
                <>
                    <Category category={row.processCategory} filtersContext={filtersContext} />
                    <span style={{ paddingLeft: 8, paddingRight: 8 }}>/</span>
                </>
            )}
            <CopyTooltip text={row.name} title={t("scenario.copyName", "Copy name to clipboard")}>
                <HighlightedName value={row.name} filterText={filtersContext.getFilter("NAME")} />
            </CopyTooltip>
        </div>
    );
}

export function SecondLine({ row }: { row: RowType }): JSX.Element {
    const { getFilter } = useScenariosFilterContext();
    const [createdBy] = getFilter("CREATED_BY", true);
    const [sortedBy] = getFilter("SORT_BY", true);
    const filteredByCreation = createdBy === row.createdBy;
    const filtersContext = useScenariosFilterContext();

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
        <Typography variant={"caption"} noWrap>
            {t("scenario.createdAt", "created {{date, relativeDate}}", { date: new Date(row.createdAt) })} {t("scenario.authorBy", "by")}{" "}
            <Author value={row.createdBy} />
        </Typography>
    );
}

function ModificationDate({ row }: { row: RowType }): JSX.Element {
    const { t } = useTranslation();

    return (
        <Typography variant={"caption"} noWrap>
            {t("scenario.modifiedAtLabel", "Last modified:")}{" "}
            {t("scenario.modifiedAt", "{{date, relativeDate}}", { date: formatDateTime(row.modificationDate) })}{" "}
            {t("scenario.authorBy", "by")} <Author value={row.modifiedBy} />
        </Typography>
    );
}
