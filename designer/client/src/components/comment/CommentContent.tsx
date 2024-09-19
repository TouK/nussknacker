import React, { useMemo } from "react";
import { isEmpty } from "lodash";
import xss from "xss";
import { PanelComment } from "./StyledComment";
import { Link, Theme, ThemeProvider, Typography, useTheme } from "@mui/material";
import ReactDOMServer, { renderToString } from "react-dom/server";
import Highlighter from "react-highlight-words";

interface Props {
    content: string;
    commentSettings: {
        substitutionPattern?: string;
        substitutionLink?: string;
    };
    searchWords?: string[];
}

const withHighlightText = (text: string, searchWords: string[], theme: Theme) => {
    const handleReplaceText = (textToReplace: string, valueToReplace: string) => {
        return textToReplace.replace(
            valueToReplace,
            renderToString(
                <Highlighter
                    searchWords={searchWords}
                    autoEscape={true}
                    textToHighlight={valueToReplace}
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

    let replacedText = text;
    const beforeHtmlTagTextRegexp = /^(.*?)(?=<[a-zA-Z][^\s>]*\b[^>]*>)/;
    const beforeHtmlTagText = text.match(beforeHtmlTagTextRegexp)?.[0];

    if (beforeHtmlTagText) {
        replacedText = handleReplaceText(replacedText, beforeHtmlTagText);
    }

    const htmlTagInsideTextRegexp = /<([a-zA-Z][^\s>]*)(?:\s[^>]*)?>(.*?)<\/\1>/;
    const htmlTagInsideText = text.match(htmlTagInsideTextRegexp)?.[2];

    if (htmlTagInsideText) {
        replacedText = handleReplaceText(replacedText, htmlTagInsideText);
    }

    const afterHtmlTagTextRegexp = /<([a-zA-Z][^\s>]*)(?:\s[^>]*)?>(.*?)<\/\1>(\s.*)?/;
    const afterHtmlTagText = text.match(afterHtmlTagTextRegexp)?.[3];

    if (afterHtmlTagText) {
        replacedText = handleReplaceText(replacedText, afterHtmlTagText);
    }

    if (!beforeHtmlTagText && !htmlTagInsideText && !afterHtmlTagText) {
        replacedText = handleReplaceText(replacedText, text);
    }

    console.log(replacedText);
    return replacedText;
};

function CommentContent({ commentSettings, content, searchWords }: Props): JSX.Element {
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

    const sanitizedContent = useMemo(
        () =>
            xss(newContent, {
                whiteList: {
                    // eslint-disable-next-line i18next/no-literal-string
                    a: ["href", "title", "target", "class"],
                },
            }),
        [newContent],
    );

    const __html = useMemo(
        () => (searchWords ? withHighlightText(sanitizedContent, searchWords, theme) : sanitizedContent),
        [sanitizedContent, searchWords, theme],
    );

    return (
        <PanelComment>
            <Typography variant={"caption"} dangerouslySetInnerHTML={{ __html }} />
        </PanelComment>
    );
}

export default CommentContent;
