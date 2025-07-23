import { useTheme } from "@mui/material";
import type { CSSProperties } from "react";
import React from "react";
import Highlighter from "react-highlight-words";

export function SearchHighlighter({
    children,
    highlights = [],
    className,
    typographyStyle = {},
    title,
    ...props
}: {
    children: string;
    highlights: string[];
    className?: string;
    typographyStyle?: CSSProperties;
    title?: string;
}) {
    const theme = useTheme();
    return (
        <Highlighter
            className={className}
            aria-label={`tool:${children}`}
            textToHighlight={children || ""}
            searchWords={highlights}
            autoEscape
            highlightTag={`span`}
            unhighlightStyle={typographyStyle}
            highlightStyle={{
                ...typographyStyle,
                color: theme.palette.warning.main,
                background: theme.palette.background.paper,
                fontWeight: "bold",
            }}
            title={title}
            {...props}
        />
    );
}
