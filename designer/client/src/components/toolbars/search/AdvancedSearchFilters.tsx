import React, { useEffect, useMemo } from "react";
import { Box, Button, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { SearchLabeledInput } from "../../sidePanels/SearchLabeledInput";
import { SearchLabel } from "../../sidePanels/SearchLabel";
import { resolveSearchQuery, searchQueryToString, selectorByName } from "./utils";
import { SearchQuery } from "./SearchResults";
import { SearchLabeledAutocomplete } from "../../sidePanels/SearchLabeledAutocomplete";
import { useSelector } from "react-redux";
import { getScenario } from "../../../reducers/selectors/graph";
import NodeUtils from "../../graph/NodeUtils";
import { uniq } from "lodash";
import { getComponentGroups } from "../../../reducers/selectors/settings";

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
    const componentsGroups = useSelector(getComponentGroups);
    const { scenarioGraph } = useSelector(getScenario);
    const allNodes = NodeUtils.nodesFromScenarioGraph(scenarioGraph);

    const displayNames = useMemo(
        () => ({
            name: t("panels.search.field.id", "Name"),
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
            componentsGroups.flatMap((componentGroup) => componentGroup.components).map((component) => component.label.toLowerCase()),
        );
    }, [componentsGroups]);

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
