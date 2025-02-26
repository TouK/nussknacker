import { useTheme } from "@mui/material";
import Highlighter from "react-highlight-words";
import React, { CSSProperties } from "react";

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
