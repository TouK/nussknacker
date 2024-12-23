import React, { useEffect, useMemo } from "react";
import { Button, Box, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { SearchLabeledInput } from "../../sidePanels/SearchLabeledInput";
import { SearchLabel } from "../../sidePanels/SearchLabel";
import { resolveSearchQuery, searchQueryToString, useNodeTypes } from "./utils";
import { SearchQuery } from "./SearchResults";
import { SearchLabeledAutocomplete } from "../../sidePanels/SearchLabeledAutocomplete";

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

    useEffect(() => {
        const searchQuery = resolveSearchQuery(filter);
        setFilterFields(searchQuery);
    }, [filter]);

    const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();

        setFilter(searchQueryToString(filterFields));
        setCollapsedHandler(false);
    };

    const handleClear = () => {
        setFilter(filterFields?.plainQuery);
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
            <SearchLabeledAutocomplete
                name="type"
                options={useNodeTypes()}
                value={filterFields?.type || []}
                setFilterFields={setFilterFields}
            >
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
            <Box sx={{ display: "flex", flexDirection: "row", justifyContent: "space-between", width: "100%", mt: 2, mb: 1 }}>
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
