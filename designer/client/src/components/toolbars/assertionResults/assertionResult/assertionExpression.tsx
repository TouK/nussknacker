import { useTheme } from "@mui/material";
import React from "react";

import type { Assertion } from "../../../../actions/nk/testCasesActions";
import { ASSERTION_SYMBOLS } from "../../../graph/node-modal/node/NodeContent/TestingContentElements/AssertionItem";
import { ScrollableHighlightedExpression } from "./ScrollableHighlightedExpression";

interface Props {
    assertion: Assertion;
}

export const AssertionExpression = ({ assertion }: Props) => {
    const operatorToDisplay = ASSERTION_SYMBOLS[assertion.operator];
    useTheme();

    const parts = [
        {
            expression: assertion.expected.expression,
            language: assertion.expected.language,
        },
        {
            expression: operatorToDisplay,
            language: "plain_text",
        },
        {
            expression: assertion.actual.expression,
            language: assertion.expected.language,
        },
    ];

    return <ScrollableHighlightedExpression parts={parts} />;
};
