import React from "react";
import { TextField, Button, Box, Typography } from "@mui/material";

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
