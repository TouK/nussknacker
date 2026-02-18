import { FormHelperText } from "@mui/material";
import React from "react";

import type { TestAssertionResult } from "../../../../http/resultsWithCountsDto";

interface Props {
    assertionResult: TestAssertionResult;
}

export const AssertionResultMessage = ({ assertionResult }: Props) => {
    if (assertionResult.type === "FailedAssertion") {
        return <FormHelperText error>{assertionResult.message}</FormHelperText>;
    }

    return null;
};
