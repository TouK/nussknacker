import React, { useMemo } from "react";
import { isEmpty } from "lodash";
import xss from "xss";
import { PanelComment } from "./StyledComment";
import { Typography } from "@mui/material";

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
            const replacement = `<a href=${commentSettings.substitutionLink} target="_blank">$1</a>`;
            return content.replace(regex, replacement);
        }
    }, [commentSettings, content]);

    const __html = useMemo(
        () =>
            xss(newContent, {
                whiteList: {
                    // eslint-disable-next-line i18next/no-literal-string
                    a: ["href", "title", "target"],
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
