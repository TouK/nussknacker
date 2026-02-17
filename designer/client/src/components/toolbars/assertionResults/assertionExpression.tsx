import { Box, Typography } from "@mui/material";
import React from "react";

import type { Assertion } from "../../../actions/nk/testCasesActions";
import { ASSERTION_SYMBOLS } from "../../graph/node-modal/node/NodeContent/TestingContentElements/AssertionItem";

interface Props {
    assertion: Assertion;
}

export const AssertionExpression = ({ assertion }: Props) => {
    const operatorToDisplay = ASSERTION_SYMBOLS[assertion.operator];

    return (
        <Box display={"flex"} gap={1}>
            <Typography variant={"body2"}>{assertion.expected.expression}</Typography>
            <Typography variant={"body2"}>{operatorToDisplay}</Typography>
            <Typography variant={"body2"}>{assertion.actual.expression}</Typography>
        </Box>
    );
};
