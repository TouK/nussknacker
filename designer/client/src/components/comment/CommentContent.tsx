import type { Theme } from "@mui/material";
import { Link, ThemeProvider, Typography, useTheme } from "@mui/material";
import type { Variant } from "@mui/material/styles/createTypography";
import { isEmpty } from "lodash";
import React, { useMemo } from "react";
import ReactDOMServer, { renderToString } from "react-dom/server";
import Highlighter from "react-highlight-words";
import xss from "xss";

import { PanelComment } from "./StyledComment";

interface Props {
    content: string;
    commentSettings: {
        substitutionPattern?: string;
        substitutionLink?: string;
    };
    searchWords?: string[];
    variant?: Variant;
}

const withHighlightText = (text: string, searchWords: string[], theme: Theme) => {
    const handleReplaceText = (textToReplace: string) => {
        return textToReplace.replace(
            textToReplace,
            renderToString(
                <Highlighter
                    searchWords={searchWords}
                    autoEscape={true}
                    textToHighlight={textToReplace}
                    highlightTag={`span`}
                    highlightStyle={{
                        color: theme.palette.warning.main,
                        background: theme.palette.background.paper,
                        fontWeight: "bold",
                    }}
                />,
            ),
        );
    };

    const beforeHtmlTagTextRegexp = /([^<>]+)(?=<|$)/g;
    return text.replaceAll(beforeHtmlTagTextRegexp, (match, p1: string) => {
        return searchWords.some((searchWord) => p1.includes(searchWord)) ? `${handleReplaceText(p1)}` : p1;
    });
};

export function useHighlightedContent(content: string, searchWords: string[]) {
    const theme = useTheme();
    const sanitizedContent = useMemo(
        () =>
            xss(content, {
                whiteList: {
                    // eslint-disable-next-line i18next/no-literal-string
                    a: ["href", "title", "target", "class"],
                },
            }),
        [content],
    );

    return useMemo(
        () => (searchWords?.length > 0 ? withHighlightText(sanitizedContent, searchWords, theme) : sanitizedContent),
        [sanitizedContent, searchWords, theme],
    );
}

function CommentContent({ commentSettings, content, searchWords, variant = "caption" }: Props): React.JSX.Element {
    const theme = useTheme();
    const newContent = useMemo(() => {
        if (isEmpty(commentSettings)) {
            return content;
        } else {
            // eslint-disable-next-line i18next/no-literal-string
            const regex = new RegExp(commentSettings.substitutionPattern, "g");

            const replacement = ReactDOMServer.renderToString(
                <ThemeProvider theme={theme}>
                    <Link href={commentSettings.substitutionLink} target="_blank">
                        $1
                    </Link>
                </ThemeProvider>,
            );

            return content.replace(regex, replacement);
        }
    }, [commentSettings, content, theme]);

    const __html = useHighlightedContent(newContent, searchWords);

    return (
        <PanelComment>
            <Typography variant={variant} dangerouslySetInnerHTML={{ __html }} />
        </PanelComment>
    );
}

export default CommentContent;
