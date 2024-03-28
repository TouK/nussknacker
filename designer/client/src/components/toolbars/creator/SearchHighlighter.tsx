import { useTheme } from "@mui/material";
import Highlighter from "react-highlight-words";
import React from "react";

export function SearchHighlighter({
    children,
    highlights = [],
    className,
}: {
    children: string;
    highlights: string[];
    className?: string;
}) {
    const theme = useTheme();
    return (
        <Highlighter
            className={className}
            textToHighlight={children || ""}
            searchWords={highlights}
            autoEscape
            highlightTag={`span`}
            highlightStyle={{
                color: theme.custom.colors.warning,
                background: theme.custom.colors.secondaryBackground,
                fontWeight: "bold",
            }}
        />
    );
}
