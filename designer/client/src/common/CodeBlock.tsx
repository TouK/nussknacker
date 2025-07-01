import { darken, Typography } from "@mui/material";
import { styled } from "@mui/material";
import type { JSX } from "react";
import React from "react";

import { CopyIconButton, useCopyClipboard } from "./copyToClipboard";
import { SyntaxHighlighter } from "./SyntaxHighlighter";

type NodePosition = {
    start: { line: number };
    end: { line: number };
};

type Props = JSX.IntrinsicElements["code"] & { node?: { position?: NodePosition } };

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

export const CodeBlock = ({ className, children, node }: Props) => {
    const { start, end } = node.position;
    const singleLineCode = start.line === end.line;
    const match = /language-(\w+)/.exec(className || "");
    const language = match?.[1] || "text";
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
                <CopyIconButton onClick={handleCopy} isCopied={isCopied} />
            </CodeBlockHeader>
            <SyntaxHighlighter language={language}>{typeof children === "string" && children}</SyntaxHighlighter>
        </CodeBlockContainer>
    );
};
