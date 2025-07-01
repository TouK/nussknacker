import { darken, useTheme } from "@mui/material";
import type { Theme } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";
import { Prism } from "react-syntax-highlighter";

import { getBorderColor } from "../containers/theme/helpers";

export const syntaxHighlighterStyle = (theme: Theme): { [key: string]: React.CSSProperties } | undefined => ({
    'code[class*="language-"]': {
        ...theme.typography.body2,
    },
    'pre[class*="language-"]': {
        backgroundColor: darken(theme.palette.background.paper, 0.4),
        borderRadius: 4,
        border: getBorderColor(theme),
        padding: theme.spacing(2),
        marginBottom: theme.spacing(2),
        marginTop: 0,
        overflowX: "auto",
    },
    pre: {
        ...theme.typography.body2,
    },
    comment: {
        color: "#d4d0ab",
    },
    prolog: {
        color: "#d4d0ab",
    },
    doctype: {
        color: "#d4d0ab",
    },
    cdata: {
        color: "#d4d0ab",
    },
    punctuation: {
        color: "#fefefe",
    },
    property: {
        color: "#ffa07a",
    },
    tag: {
        color: "#ffa07a",
    },
    constant: {
        color: "#ffa07a",
    },
    symbol: {
        color: "#ffa07a",
    },
    deleted: {
        color: "#ffa07a",
    },
    boolean: {
        color: "#00e0e0",
    },
    number: {
        color: "#00e0e0",
    },
    selector: {
        color: "#abe338",
    },
    "attr-name": {
        color: "#abe338",
    },
    string: {
        color: "#abe338",
    },
    char: {
        color: "#abe338",
    },
    builtin: {
        color: "#abe338",
    },
    inserted: {
        color: "#abe338",
    },
    operator: {
        color: "#00e0e0",
    },
    entity: {
        color: "#00e0e0",
        cursor: "help",
    },
    url: {
        color: "#00e0e0",
    },
    ".language-css .token.string": {
        color: "#00e0e0",
    },
    ".style .token.string": {
        color: "#00e0e0",
    },
    variable: {
        color: "#00e0e0",
    },
    atrule: {
        color: "#ffd700",
    },
    "attr-value": {
        color: "#ffd700",
    },
    function: {
        color: "#ffd700",
    },
    keyword: {
        color: "#00e0e0",
    },
    regex: {
        color: "#ffd700",
    },
    important: {
        color: "#ffd700",
        fontWeight: "bold",
    },
    bold: {
        fontWeight: "bold",
    },
    italic: {
        fontStyle: "italic",
    },
});

interface Props {
    language: string;
    customStyle?: React.CSSProperties;
}

export const SyntaxHighlighter = ({ language, customStyle, children }: PropsWithChildren<Props>) => {
    const theme = useTheme();

    return (
        <Prism language={language} style={syntaxHighlighterStyle(theme)} customStyle={customStyle}>
            {typeof children === "string" && children}
        </Prism>
    );
};
