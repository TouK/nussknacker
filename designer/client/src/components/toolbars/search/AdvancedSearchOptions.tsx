import React from "react";
import { TextField, Button, Box, Typography } from "@mui/material";

const transformInput = (input: string, fieldName: string) => {
    return input === "" ? "" : `${fieldName}:(${input})`;
};

export function AdvancedSearchOptions({
    setFilter,
    setCollapsedHandler,
}: {
    setFilter: React.Dispatch<React.SetStateAction<string>>;
    setCollapsedHandler: React.Dispatch<React.SetStateAction<boolean>>;
}) {
    const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();

        const formData = new FormData(event.currentTarget);
        const idInput = (formData.get("id") as string) || "";
        const descriptionInput = (formData.get("description") as string) || "";
        const typeInput = (formData.get("type") as string) || "";
        const paramNameInput = (formData.get("paramName") as string) || "";
        const paramValueInput = (formData.get("paramValue") as string) || "";
        const outputValueInput = (formData.get("outputValue") as string) || "";
        const edgeExpressionInput = (formData.get("edgeExpression") as string) || "";

        const transformedIdInput = transformInput(idInput, "id");
        const transformedDescriptionInput = transformInput(descriptionInput, "description");
        const transformedTypeInput = transformInput(typeInput, "type");
        const transformedParamNameInput = transformInput(paramNameInput, "paramName");
        const transformedParamValueInput = transformInput(paramValueInput, "paramValue");
        const transformedOutputValueInput = transformInput(outputValueInput, "outputValue");
        const transformedEdgeExpressionInput = transformInput(edgeExpressionInput, "edgeExpression");

        const transformedInputs = [
            transformedIdInput,
            transformedDescriptionInput,
            transformedTypeInput,
            transformedParamNameInput,
            transformedParamValueInput,
            transformedOutputValueInput,
            transformedEdgeExpressionInput,
        ];

        const finalText = transformedInputs.join(" ").trimEnd();

        setFilter(finalText);
    };

    const handleCancel = () => {
        setCollapsedHandler(false);
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
            <TextField sx={{ m: 1 }} size="small" label="id" name="id" />
            <TextField size="small" sx={{ m: 1 }} label="paramValue" name="paramValue" />
            <TextField size="small" sx={{ m: 1 }} label="paramName" name="paramName" />
            <TextField size="small" sx={{ m: 1 }} label="outputValue" name="outputValue" />
            <TextField size="small" sx={{ m: 1 }} label="description" name="description" />
            <TextField size="small" sx={{ m: 1 }} label="type" name="type" />
            <TextField size="small" sx={{ m: 1 }} label="edgeExpression" name="edgeExpression" />
            <Box sx={{ display: "flex", flexDirection: "row", justifyContent: "space-between", width: "85%", mt: 1, mb: 1 }}>
                <Button sx={{ width: "45%" }} size="small" variant="outlined" onClick={handleCancel}>
                    Cancel
                </Button>
                <Button variant="contained" size="small" sx={{ width: "45%" }} type="submit">
                    Submit
                </Button>
            </Box>
        </Box>
    );
}
