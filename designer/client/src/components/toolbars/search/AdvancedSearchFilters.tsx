import React, { MutableRefObject, useEffect, useMemo } from "react";
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
            paramName: t("panels.search.field.paramName", "Label"),
            paramValue: t("panels.search.field.paramValue", "Value"),
            outputValue: t("panels.search.field.outputValue", "Output"),
            edgeExpression: t("panels.search.field.edgeExpression", "Edge"),
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
            <SearchLabeledInput name="paramValue" value={filterFields?.paramValue || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["paramValue"]} />
            </SearchLabeledInput>
            <SearchLabeledAutocomplete
                name="type"
                options={useNodeTypes()}
                value={filterFields?.type || []}
                setFilterFields={setFilterFields}
            >
                <SearchLabel label={displayNames["type"]} />
            </SearchLabeledAutocomplete>
            <SearchLabeledInput name="paramName" value={filterFields?.paramName || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["paramName"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="description" value={filterFields?.description || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["description"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="outputValue" value={filterFields?.outputValue || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["outputValue"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="edgeExpression" value={filterFields?.edgeExpression || []} setFilterFields={setFilterFields}>
                <SearchLabel label={displayNames["edgeExpression"]} />
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
