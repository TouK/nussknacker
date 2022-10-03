import { Box } from "@mui/material";
import React, { PropsWithChildren } from "react";
import Highlighter from "react-highlight-words";

function HighlightTag({ children }: PropsWithChildren<unknown>): JSX.Element {
    return (
        <Box component="strong" sx={{ color: "primary.main" }}>
            {children}
        </Box>
    );
}

export function Highlight({ value, filterText }: { value: string; filterText: string }): JSX.Element {
    return (
        <Highlighter
            autoEscape
            textToHighlight={value.toString()}
            searchWords={filterText?.toString().trim().split(/\s/) || []}
            highlightTag={HighlightTag}
        />
    );
}
