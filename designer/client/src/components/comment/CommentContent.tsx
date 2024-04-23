import React, { useMemo } from "react";
import { isEmpty } from "lodash";
import xss from "xss";
import { PanelComment } from "./StyledComment";
import { Link, ThemeProvider, Typography } from "@mui/material";
import ReactDOMServer from "react-dom/server";
import { NuThemeProvider } from "../../containers/theme/nuThemeProvider";

interface Props {
    content: string;
    commentSettings: { substitutionPattern?: string; substitutionLink?: string };
}

function CommentContent({ commentSettings, content }: Props): JSX.Element {
    const newContent = useMemo(() => {
        if (isEmpty(commentSettings)) {
            return content;
        } else {
            // eslint-disable-next-line i18next/no-literal-string
            const regex = new RegExp(commentSettings.substitutionPattern, "g");

            const replacement = ReactDOMServer.renderToString(
                <NuThemeProvider>
                    <Link href={commentSettings.substitutionLink} target="_blank">
                        $1
                    </Link>
                </NuThemeProvider>,
            );

            return content.replace(regex, replacement);
        }
    }, [commentSettings, content]);

    const __html = useMemo(
        () =>
            xss(newContent, {
                whiteList: {
                    // eslint-disable-next-line i18next/no-literal-string
                    a: ["href", "title", "target", "class"],
                },
            }),
        [newContent],
    );

    return (
        <PanelComment>
            <Typography variant={"caption"} dangerouslySetInnerHTML={{ __html }} />
        </PanelComment>
    );
}

export default CommentContent;
