import { ContentCopy, Done } from "@mui/icons-material";
import type { Theme } from "@mui/material";
import { darken, Typography } from "@mui/material";
import { styled } from "@mui/material";
import { useTheme } from "@mui/material";
import { IconButton, Tooltip } from "@mui/material";
import type { JSX } from "react";
import React from "react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";

import { useCopyClipboard } from "../components/notifications/copyTooltip";
import { getBorderColor } from "../containers/theme/helpers";

type NodePosition = {
    start: { line: number };
    end: { line: number };
};

type Props = JSX.IntrinsicElements["code"] & { node?: { position?: NodePosition } };

const syntaxHighlighterStyle = (theme: Theme): { [key: string]: React.CSSProperties } | undefined => ({
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

const StyledSingleLineBlock = styled("code")(({ theme }) => ({
    backgroundColor: theme.palette.grey["700"],
}));

const CodeBlockContainer = styled("div")(() => ({
    position: "relative",
    "&:hover .copy-button": {
        opacity: 1,
    },
}));

const CodeBlockHeader = styled("div")(({ theme }) => ({
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    backgroundColor: darken(theme.palette.background.paper, 0.6),
    padding: `${theme.spacing(0.5)} ${theme.spacing(1.5)}`,
    borderTopLeftRadius: 4,
    borderTopRightRadius: 4,
    borderBottom: "1px solid rgba(255, 255, 255, 0.1)",
}));

const LanguageLabel = styled(Typography)(({ theme }) => ({
    ...theme.typography.caption,
    color: theme.palette.common.white,
    opacity: 0.7,
    textTransform: "uppercase",
    fontFamily: "monospace",
    letterSpacing: 1,
}));

const CopyIconButton = styled(IconButton)(({ theme }) => ({
    color: theme.palette.common.white,
    padding: theme.spacing(0.5),
    "&:hover": {
        backgroundColor: "rgba(255, 255, 255, 0.1)",
    },
}));

export const CodeBlock = ({ className, children, node }: Props) => {
    const { start, end } = node.position;
    const singleLineCode = start.line === end.line;
    const match = /language-(\w+)/.exec(className || "");
    const language = match?.[1] || "text";
    const theme = useTheme();
    const [isCopied, copy] = useCopyClipboard();

    const handleCopy = () => {
        if (typeof children === "string") {
            copy(children);
        }
    };

    return singleLineCode ? (
        <StyledSingleLineBlock>{children}</StyledSingleLineBlock>
    ) : (
        <CodeBlockContainer>
            <CodeBlockHeader>
                <LanguageLabel>{language}</LanguageLabel>
                <Tooltip title={isCopied ? "Copied!" : "Copy code"}>
                    <CopyIconButton size="small" onClick={handleCopy} aria-label="copy code" className={"copy-button"}>
                        {isCopied ? <Done fontSize="small" /> : <ContentCopy fontSize="small" />}
                    </CopyIconButton>
                </Tooltip>
            </CodeBlockHeader>
            <SyntaxHighlighter language={language} style={syntaxHighlighterStyle(theme)}>
                {typeof children === "string" && children}
            </SyntaxHighlighter>
        </CodeBlockContainer>
    );
};
