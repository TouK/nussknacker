import { Box } from "@mui/material";
import React from "react";

import type { TestAssertionResult } from "../../../http/resultsWithCountsDto";

interface Props {
    assertionResult: TestAssertionResult;
}

export const AssertionStatus = ({ assertionResult }: Props) => {
    return <Box>{assertionResult.type === "FailedAssertion" ? assertionResult.message : "ok"}</Box>;
};
