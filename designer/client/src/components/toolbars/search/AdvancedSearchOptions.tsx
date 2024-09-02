import React from "react";
import { useRef } from "react";
import { TextField, Button, Box, Typography } from "@mui/material";

const transformInput = (input: string, fieldName: string) => {
    return input == "" ? "" : `${fieldName}:(${input})`;
};

export function AdvancedSearchOptions({
    setFilter,
    setCollapsedHandler,
}: {
    setFilter: React.Dispatch<React.SetStateAction<string>>;
    setCollapsedHandler: React.Dispatch<React.SetStateAction<boolean>>;
}) {
    const idInputRef = useRef<HTMLInputElement>();
    const descriptionInputRef = useRef<HTMLInputElement>();
    const typeInputRef = useRef<HTMLInputElement>();
    const paramNameInputRef = useRef<HTMLInputElement>();
    const paramValueInputRef = useRef<HTMLInputElement>();
    const outputValueInputRef = useRef<HTMLInputElement>();
    const edgeExpressionInputRef = useRef<HTMLInputElement>();

    const handleSubmit = () => {
        const idInput = idInputRef.current?.value || "";
        const descriptionInput = descriptionInputRef.current?.value || "";
        const typeInput = typeInputRef.current?.value || "";
        const paramNameInput = paramNameInputRef.current?.value || "";
        const paramValueInput = paramValueInputRef.current?.value || "";
        const outputValueInput = outputValueInputRef.current?.value || "";
        const edgeExpressionInput = edgeExpressionInputRef.current?.value || "";

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
            <TextField sx={{ m: 1 }} size="small" label="id" inputRef={idInputRef} />
            <TextField size="small" sx={{ m: 1 }} label="paramValue" inputRef={paramValueInputRef} />
            <TextField size="small" sx={{ m: 1 }} label="paramName" inputRef={paramNameInputRef} />
            <TextField size="small" sx={{ m: 1 }} label="outputValue" inputRef={outputValueInputRef} />
            <TextField size="small" sx={{ m: 1 }} label="description" inputRef={descriptionInputRef} />
            <TextField size="small" sx={{ m: 1 }} label="type" inputRef={typeInputRef} />
            <TextField size="small" sx={{ m: 1 }} label="edgeExpression" inputRef={edgeExpressionInputRef} />
            <Box sx={{ display: "flex", flexDirection: "row", justifyContent: "space-between", width: "85%", mt: 1, mb: 1 }}>
                <Button sx={{ width: "45%" }} size="small" variant="outlined" onClick={handleCancel}>
                    Cancel
                </Button>
                <Button variant="contained" size="small" sx={{ width: "45%" }} onClick={handleSubmit}>
                    Submit
                </Button>
            </Box>
        </Box>
    );
}
