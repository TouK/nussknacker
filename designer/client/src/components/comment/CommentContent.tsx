import React, { useMemo } from "react";
import { isEmpty } from "lodash";
import { styled } from "@mui/material";
import xss from "xss";

interface Props {
    content: string;
    commentSettings: { substitutionPattern?: string; substitutionLink?: string };
}

const PanelComment = styled("div")`
    margin-top: 1px;
    font-size: 12px;
    word-break: break-word;
    p {
        width: 90%;
        margin-left: 0;
        margin-right: 0;
    }
`;

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
            <p dangerouslySetInnerHTML={{ __html }} />
        </PanelComment>
    );
}

export default CommentContent;
