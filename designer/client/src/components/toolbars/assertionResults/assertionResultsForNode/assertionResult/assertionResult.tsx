import { Box, Typography } from "@mui/material";
import React from "react";

import type { Assertion } from "../../../../../actions/nk/testCasesActions";
import type { TestAssertionResult } from "../../../../../http/resultsWithCountsDto";
import type { WithUuid } from "../../../../graph/node-modal/appendUuid";
import { InfoTooltip } from "../../../../graph/node-modal/editors/InfoTooltip/InfoTooltip";
import { AssertionExpression } from "./assertionExpression";
import { AssertionResultMessage } from "./assertionResultMessage";
import { AssertionStatusIcon } from "./AssertionStatusIcon";

interface Props {
    assertionResult: TestAssertionResult;
    testCaseAssertion: WithUuid<Assertion>;
}

export const AssertionResult = ({ assertionResult, testCaseAssertion }: Props) => {
    const tooltipContent = (
        <Box p={1}>
            {testCaseAssertion.description && <AssertionExpression assertion={testCaseAssertion} />}
            <AssertionResultMessage assertionResult={assertionResult} />
        </Box>
    );

    const displayTooltip = assertionResult.type === "FailedAssertion" || testCaseAssertion.description;

    const content = (
        <Box
            data-testid={"assertionResult"}
            display={"flex"}
            gap={0.75}
            alignItems={"center"}
            sx={{ cursor: displayTooltip ? "pointer" : "default" }}
        >
            <AssertionStatusIcon isSuccess={assertionResult.type === "SuccessfulAssertion"} />
            {testCaseAssertion.description ? (
                <Typography variant={"caption"}>{testCaseAssertion.description}</Typography>
            ) : (
                <AssertionExpression assertion={testCaseAssertion} />
            )}
        </Box>
    );

    return (
        <>
            {displayTooltip ? (
                <InfoTooltip variant={"click"} title={tooltipContent} placement={"right"}>
                    {content}
                </InfoTooltip>
            ) : (
                content
            )}
        </>
    );
};
