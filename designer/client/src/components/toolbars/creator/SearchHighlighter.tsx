import { useTheme } from "@mui/material";
import type { CSSProperties } from "react";
import React from "react";
import Highlighter from "react-highlight-words";

export function SearchHighlighter({
    children,
    highlights = [],
    className,
    typographyStyle = {},
    highlighterStyle = {},
    title,
    ...props
}: {
    children: string;
    highlights: string[];
    className?: string;
    typographyStyle?: CSSProperties | ((theme: Theme) => CSSProperties);
    highlightStyle?: CSSProperties | ((theme: Theme) => CSSProperties);
    title?: string;
}) {
    const theme = useTheme();
    const unhighlightStyle = typeof typographyStyle === "function" ? typographyStyle(theme) : typographyStyle;
    const highlightOverrideStyle = typeof highlightStyle === "function" ? highlightStyle(theme) : highlightStyle;
    return (
        <Highlighter
            className={className}
            aria-label={`tool:${children}`}
            textToHighlight={children || ""}
            searchWords={highlights}
            autoEscape
            highlightTag={`span`}
            unhighlightStyle={unhighlightStyle}
            highlightStyle={{
                fontWeight: "bold",
                ...unhighlightStyle,
                color: theme.palette.warning.main,
                background: theme.palette.background.paper,
                ...highlightOverrideStyle,
            }}
            title={title}
            {...props}
        />
    );
}
