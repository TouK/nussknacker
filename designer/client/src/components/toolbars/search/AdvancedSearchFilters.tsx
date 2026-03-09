import { Box, Button, Typography } from "@mui/material";
import { uniq } from "lodash";
import React, { useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { getScenario } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import NodeUtils from "../../graph/NodeUtils";
import { SearchLabel } from "../../sidePanels/SearchLabel";
import { SearchLabeledAutocomplete } from "../../sidePanels/SearchLabeledAutocomplete";
import { SearchLabeledInput } from "../../sidePanels/SearchLabeledInput";
import type { SearchQuery } from "./SearchResults";
import { resolveSearchQuery, searchQueryToString, selectorByName } from "./utils";

export function AdvancedSearchFilters({
    filterFields,
    setFilterFields,
    filter,
    setFilter,
    setCollapsedHandler,
}: {
    filterFields: SearchQuery;
    setFilterFields: React.Dispatch<React.SetStateAction<SearchQuery>>;
    filter: string;
    setFilter: React.Dispatch<React.SetStateAction<string>>;
    setCollapsedHandler: React.Dispatch<React.SetStateAction<boolean>>;
}) {
    const { t } = useTranslation();
    const { componentGroups } = useAppSelector(getProcessDefinitionData);
    const { scenarioGraph } = useAppSelector(getScenario);
    const allNodes = NodeUtils.nodesFromScenarioGraph(scenarioGraph);

    const displayNames = useMemo(
        () => ({
            id: t("panels.search.field.id", "Id"),
            name: t("panels.search.field.name", "Name"),
            description: t("panels.search.field.description", "Description"),
            type: t("panels.search.field.type", "Type"),
            label: t("panels.search.field.paramName", "Label"),
            value: t("panels.search.field.paramValue", "Value"),
            output: t("panels.search.field.outputValue", "Output"),
            edge: t("panels.search.field.edgeExpression", "Edge"),
        }),
        [t],
    );

    const componentLabels = useMemo(() => {
        return new Set(
            componentGroups.flatMap((componentGroup) => componentGroup.components).map((component) => component.label.toLowerCase()),
        );
    }, [componentGroups]);

    const nodeTypes = useMemo(() => {
        const availableTypes = allNodes
            .flatMap((node) =>
                selectorByName("type")
                    .flatMap((s) => s.selector(node))
                    .filter((item) => item !== undefined),
            )
            .map((selectorResult) => (typeof selectorResult === "string" ? selectorResult : selectorResult?.expression))
            .filter((type) => componentLabels.has(type.toLowerCase()));

        return uniq(availableTypes).sort();
    }, [allNodes, componentLabels]);

    useEffect(() => {
        const searchQuery = resolveSearchQuery(filter);
        setFilterFields(searchQuery);
    }, [filter, setFilterFields]);

    const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();

        setFilter(searchQueryToString(filterFields));
        setCollapsedHandler(false);
    };

    const handleClear = () => {
        setFilter(filterFields?.plainQuery);
        setFilterFields({ plainQuery: filterFields?.plainQuery });
    };

    return (
        <Box
            component="form"
            onSubmit={handleSubmit}
            sx={{
                display: "flex",
                flexDirection: "column",
                maxWidth: "300px",
                justifyContent: "center",
                alignItems: "center",
            }}
        >
            <Typography fontWeight="bold">{t("search.panel.advancedFilters.label", "Advanced Search")}</Typography>
            <SearchLabeledInput name="id" value={filterFields?.id || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["id"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="name" value={filterFields?.name || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["name"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="value" value={filterFields?.value || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["value"]} />
            </SearchLabeledInput>
            <SearchLabeledAutocomplete name="type" options={nodeTypes} value={filterFields?.type || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["type"]} />
            </SearchLabeledAutocomplete>
            <SearchLabeledInput name="label" value={filterFields?.label || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["label"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="description" value={filterFields?.description || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["description"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="output" value={filterFields?.output || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["output"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="edge" value={filterFields?.edge || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["edge"]} />
            </SearchLabeledInput>
            <Box
                sx={{
                    display: "flex",
                    flexDirection: "row",
                    justifyContent: "space-between",
                    width: "100%",
                    mt: 2,
                    mb: 1,
                }}
            >
                <Button sx={{ width: "45%" }} size="small" variant="outlined" onClick={handleClear}>
                    {t("search.panel.advancedFilters.clearButton.label", "Clear")}
                </Button>
                <Button name="cancel-button" variant="contained" size="small" sx={{ width: "45%" }} type="submit">
                    {t("search.panel.advancedFilters.submitButton.label", "Submit")}
                </Button>
            </Box>
        </Box>
    );
}
