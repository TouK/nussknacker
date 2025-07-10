import { darken, Stack, styled, Typography } from "@mui/material";
import type { JSX } from "react";
import React, { useMemo } from "react";

import { SANITIZED_PASSWORD_TAG_NAME } from "../components/graph/node-modal/MarkdownStyled";
import { useTextSanitizer } from "../components/graph/node-modal/useTextSanitizer";
import { CopyIconButton, useCopyClipboard } from "./copyToClipboard";
import { ShowPasswordsButton } from "./copyToClipboard/ShowPasswordsButton";
import { InlineButtonsWrapper } from "./InlineButtonsWrapper";
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
    backgroundColor: darken(theme.palette.background.paper, 0.4),
    padding: `${theme.spacing(0.5)} ${theme.spacing(1.5)}`,
    borderTopLeftRadius: 4,
    borderTopRightRadius: 4,
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
    const text = useMemo(() => (typeof children !== "string" ? "" : children), [children]);

    const { visible, setVisible, sanitize } = useTextSanitizer();

    const sanitizedMatches = useMemo(() => {
        const tagName = SANITIZED_PASSWORD_TAG_NAME;
        const re = new RegExp(`<${tagName}>(.*)</${tagName}>`, "g");
        return [...text.matchAll(re)];
    }, [text]);

    const sanitizedText = useMemo(() => {
        return sanitizedMatches.reduce((text, [search, password]) => text.replace(search, sanitize(password)), text);
    }, [sanitizedMatches, sanitize, text]);

    const [isCopied, copy] = useCopyClipboard();
    const handleCopy = () => copy(sanitizedText);

    const buttons = (
        <>
            {sanitizedMatches.length > 0 ? <ShowPasswordsButton onClick={() => setVisible((v) => !v)} visible={visible} /> : null}
            <CopyIconButton onClick={handleCopy} isCopied={isCopied} />
        </>
    );
    return singleLineCode ? (
        <>
            <StyledSingleLineBlock>{sanitizedText}</StyledSingleLineBlock>
            <InlineButtonsWrapper>{buttons}</InlineButtonsWrapper>
        </>
    ) : (
        <CodeBlockContainer>
            <CodeBlockHeader>
                <LanguageLabel>{language}</LanguageLabel>
                <Stack direction="row">{buttons}</Stack>
            </CodeBlockHeader>
            <SyntaxHighlighter language={language} customStyle={{ margin: 0 }}>
                {sanitizedText}
            </SyntaxHighlighter>
        </CodeBlockContainer>
    );
};
