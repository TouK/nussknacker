import { styled } from "@mui/material";
import type { DetailedHTMLProps, HTMLAttributes, PropsWithoutRef } from "react";
import React from "react";
import Highlighter from "react-highlight-words";

interface HighlighterComponentProps extends PropsWithoutRef<DetailedHTMLProps<HTMLAttributes<HTMLSpanElement>, HTMLSpanElement>> {
    children: string;
    highlights: string[];
}

function HighlighterComponent({ children, highlights = [], className, ...props }: HighlighterComponentProps) {
    const classNamePrefix = `Highlighter`;
    return highlights.filter(Boolean).length > 0 ? (
        <Highlighter
            className={`${classNamePrefix}-root ${className}`}
            textToHighlight={children || ""}
            searchWords={highlights}
            autoEscape
            highlightTag={`span`}
            activeClassName={`${classNamePrefix}-active`}
            highlightClassName={`${classNamePrefix}-highlight`}
            unhighlightClassName={`${classNamePrefix}-unhighlight`}
            {...props}
        />
    ) : (
        <span className={`${classNamePrefix}-root ${classNamePrefix}-pristine ${className}`} {...props}>
            {children}
        </span>
    );
}

export const SearchHighlighter = styled(HighlighterComponent)(({ theme }) => ({
    ".Highlighter-highlight": {
        fontWeight: "bold",
        color: theme.palette.warning.main,
        background: theme.palette.background.paper,
    },
}));
