import { Box, Typography } from "@mui/material";
import React from "react";

import type { Assertion } from "../../../../actions/nk/testCasesActions";
import { SyntaxHighlighter } from "../../../../common/SyntaxHighlighter";
import { ASSERTION_SYMBOLS } from "../../../graph/node-modal/node/NodeContent/TestingContentElements/AssertionItem";

interface Props {
    assertion: Assertion;
}

export const AssertionExpression = ({ assertion }: Props) => {
    const operatorToDisplay = ASSERTION_SYMBOLS[assertion.operator];

    return (
        <Box display={"flex"} sx={{ overflowX: "auto" }}>
            <SyntaxHighlighter language={assertion.expected.language} staticHighlightOptions={{ showGutter: false }}>
                {assertion.expected.expression}
            </SyntaxHighlighter>
            <SyntaxHighlighter language={"plain_text"} staticHighlightOptions={{ showGutter: false }}>
                {operatorToDisplay}
            </SyntaxHighlighter>
            <SyntaxHighlighter language={assertion.expected.language} staticHighlightOptions={{ showGutter: false }}>
                {assertion.actual.expression}
            </SyntaxHighlighter>
        </Box>
    );
};
