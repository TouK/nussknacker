import React, { MutableRefObject, useEffect, useMemo } from "react";
import { Button, Box, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { SearchLabeledInput } from "../../sidePanels/SearchLabeledInput";
import { SearchLabel } from "../../sidePanels/SearchLabel";
import { useNodeTypes, resolveSearchQuery } from "./utils";
import { SearchLabeledAutocomplete } from "../../sidePanels/SearchLabeledAutocomplete";

const transformInput = (input: string, fieldName: string) => {
    return input === "" ? "" : `${fieldName}:(${input})`;
};

function extractSimpleSearchQuery(text: string): string {
    const regex = /(\w+):\(([^)]*)\)/g;
    let match: RegExpExecArray | null;
    let lastIndex = 0;

    while ((match = regex.exec(text)) !== null) {
        lastIndex = regex.lastIndex;
    }

    const rest = text.slice(lastIndex).trim();

    return rest;
}

export function AdvancedSearchFilters({
    filter,
    setFilter,
    setCollapsedHandler,
    refForm,
}: {
    filter: string;
    setFilter: React.Dispatch<React.SetStateAction<string>>;
    setCollapsedHandler: React.Dispatch<React.SetStateAction<boolean>>;
    refForm: MutableRefObject<HTMLFormElement>;
}) {
    const { t } = useTranslation();
    //const refForm = useRef<HTMLFormElement>(null);

    const displayNames = useMemo(
        () => ({
            name: t("panels.search.field.id", "Name"),
            description: t("panels.search.field.description", "Description"),
            type: t("panels.search.field.type", "Type"),
            componentGroup: t("panels.search.field.componentGroup", "Component Group"),
            paramName: t("panels.search.field.paramName", "Label"),
            paramValue: t("panels.search.field.paramValue", "Value"),
            outputValue: t("panels.search.field.outputValue", "Output"),
            edgeExpression: t("panels.search.field.edgeExpression", "Edge"),
        }),
        [t],
    );

    //Here be dragons: direct DOM manipulation
    useEffect(() => {
        if (refForm.current) {
            const searchQuery = resolveSearchQuery(filter);
            const formElements = refForm.current.elements;

            Array.from(formElements).forEach((element: HTMLInputElement) => {
                if (element.name in searchQuery) {
                    element.value = (searchQuery[element.name] || []).join(",");
                } else {
                    element.value = "";
                }
            });
        }
    }, [filter]);

    const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();

        const formData = new FormData(event.currentTarget);

        const transformedInputs = Array.from(formData.entries())
            .map(([fieldName, fieldValue]) => {
                const input = (fieldValue as string) || "";
                return transformInput(input, fieldName);
            })
            .filter((input) => input !== "");

        const finalText = transformedInputs.join(" ").trim() + " " + extractSimpleSearchQuery(filter);

        setFilter(finalText);
        setCollapsedHandler(false);
    };

    const handleClear = () => {
        setFilter(extractSimpleSearchQuery(filter));

        refForm.current.reset();
    };

    return (
        <Box
            ref={refForm}
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
            <SearchLabeledInput name="name">
                <SearchLabel label={displayNames["name"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="paramValue">
                <SearchLabel label={displayNames["paramValue"]} />
            </SearchLabeledInput>
            <SearchLabeledAutocomplete name="componentGroup" values={useNodeTypes("componentGroup")}>
                <SearchLabel label={displayNames["componentGroup"]} />
            </SearchLabeledAutocomplete>
            <SearchLabeledAutocomplete name="type" values={useNodeTypes("type")}>
                <SearchLabel label={displayNames["type"]} />
            </SearchLabeledAutocomplete>
            <SearchLabeledInput name="paramName">
                <SearchLabel label={displayNames["paramName"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="description">
                <SearchLabel label={displayNames["description"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="outputValue">
                <SearchLabel label={displayNames["outputValue"]} />
            </SearchLabeledInput>
            <SearchLabeledInput name="edgeExpression">
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
