import React, { useMemo, useRef } from "react";
import { Button, Box, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { SearchLabeledInput } from "../../sidePanels/SearchLabeledInput";
import { SearchLabel } from "../../sidePanels/SearchLabel";

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
}: {
    filter: string;
    setFilter: React.Dispatch<React.SetStateAction<string>>;
    setCollapsedHandler: React.Dispatch<React.SetStateAction<boolean>>;
}) {
    const { t } = useTranslation();
    const idRef = useRef<HTMLInputElement>(null);
    const descriptionRef = useRef<HTMLInputElement>(null);
    const paramNameRef = useRef<HTMLInputElement>(null);
    const paramValueRef = useRef<HTMLInputElement>(null);
    const outputValueRef = useRef<HTMLInputElement>(null);
    const typeRef = useRef<HTMLInputElement>(null);
    const edgeExpressionRef = useRef<HTMLInputElement>(null);

    const displayNames = useMemo(
        () => ({
            id: t("panels.search.field.id", "Name"),
            description: t("panels.search.field.description", "Description"),
            type: t("panels.search.field.type", "Type"),
            paramName: t("panels.search.field.paramName", "Label"),
            paramValue: t("panels.search.field.paramValue", "Value"),
            outputValue: t("panels.search.field.outputValue", "Output"),
            edgeExpression: t("panels.search.field.edgeExpression", "Edge"),
        }),
        [t],
    );

    console.log(displayNames);

    const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();

        const formData = new FormData(event.currentTarget);

        console.log(formData);

        const transformedInputs = Array.from(formData.entries())
            .map(([fieldName, fieldValue]) => {
                const input = (fieldValue as string) || "";
                return transformInput(input, fieldName);
            })
            .filter((input) => input !== "");

        const finalText = transformedInputs.join(" ").trim() + " " + extractSimpleSearchQuery(filter);

        setFilter(finalText);
    };

    const handleCancel = () => {
        idRef.current.value = "";
        descriptionRef.current.value = "";
        paramNameRef.current.value = "";
        paramValueRef.current.value = "";
        outputValueRef.current.value = "";
        typeRef.current.value = "";
        edgeExpressionRef.current.value = "";

        setFilter(extractSimpleSearchQuery(filter));
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
            <Typography fontWeight="bold" sx={{ m: 1 }}>
                Advanced Search
            </Typography>
            <SearchLabeledInput ref={idRef} name="id">
                <SearchLabel label={displayNames["id"]} />
            </SearchLabeledInput>
            <SearchLabeledInput ref={descriptionRef} name="description">
                <SearchLabel label={displayNames["description"]} />
            </SearchLabeledInput>
            <SearchLabeledInput ref={paramNameRef} name="paramName">
                <SearchLabel label={displayNames["paramName"]} />
            </SearchLabeledInput>
            <SearchLabeledInput ref={paramValueRef} name="paramValue">
                <SearchLabel label={displayNames["paramValue"]} />
            </SearchLabeledInput>
            <SearchLabeledInput ref={outputValueRef} name="outputValue">
                <SearchLabel label={displayNames["outputValue"]} />
            </SearchLabeledInput>
            <SearchLabeledInput ref={typeRef} name="type">
                <SearchLabel label={displayNames["type"]} />
            </SearchLabeledInput>
            <SearchLabeledInput ref={edgeExpressionRef} name="edgeExpression">
                <SearchLabel label={displayNames["edgeExpression"]} />
            </SearchLabeledInput>
            <Box sx={{ display: "flex", flexDirection: "row", justifyContent: "space-between", width: "80%", mt: 2, mb: 1 }}>
                <Button sx={{ width: "45%" }} size="small" variant="outlined" onClick={handleCancel}>
                    Clear
                </Button>
                <Button variant="contained" size="small" sx={{ width: "45%" }} type="submit">
                    Submit
                </Button>
            </Box>
        </Box>
    );
}
