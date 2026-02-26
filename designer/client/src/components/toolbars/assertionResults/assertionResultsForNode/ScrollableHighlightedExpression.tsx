import { Box, useTheme } from "@mui/material";
import React from "react";

import { SyntaxHighlighter } from "../../../../common/SyntaxHighlighter";
import { getScrollStyle } from "../../../graph/node-modal/node/StyledHeader";

interface HighlightPart {
    expression: string;
    language: string;
}

interface Props {
    parts: HighlightPart[];
}

export const ScrollableHighlightedExpression = ({ parts }: Props) => {
    const theme = useTheme();

    return (
        <Box display={"flex"} overflow={"auto"} sx={getScrollStyle(theme)}>
            {parts.map((part, index) => (
                <SyntaxHighlighter
                    key={index}
                    customStyle={{ border: 0 }}
                    language={part.language}
                    staticHighlightOptions={{ showGutter: false }}
                >
                    {part.expression}
                </SyntaxHighlighter>
            ))}
        </Box>
    );
};
