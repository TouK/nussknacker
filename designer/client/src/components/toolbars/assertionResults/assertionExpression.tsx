import { Box, Typography } from "@mui/material";
import React from "react";

import type { Assertion } from "../../../actions/nk/testCasesActions";

interface Props {
    assertion: Assertion;
}

export const AssertionExpression = ({ assertion }: Props) => {
    return (
        <Box>
            <Typography>{assertion.expected.expression}</Typography>
            <Typography>{assertion.operator}</Typography>
            <Typography>{assertion.actual.expression}</Typography>
        </Box>
    );
};
