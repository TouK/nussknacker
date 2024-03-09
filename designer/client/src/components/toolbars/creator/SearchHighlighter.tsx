import { useTheme } from "@mui/material";
import Highlighter from "react-highlight-words";
import React from "react";

export function SearchHighlighter({ children, highlights = [] }: { children: string; highlights: string[] }) {
    const theme = useTheme();
    return (
        <Highlighter
            textToHighlight={children || ""}
            searchWords={highlights}
            highlightTag={`span`}
            highlightStyle={{
                color: theme.custom.colors.warning,
                background: theme.custom.colors.secondaryBackground,
                fontWeight: "bold",
            }}
        />
    );
}
