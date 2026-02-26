import { Box, Typography } from "@mui/material";
import React, { Fragment } from "react";

import { getTestCaseAssertionsForNode } from "../../../../reducers/selectors/testCases";
import { getTestAssertionResultsForNode } from "../../../../reducers/selectors/testing";
import { useAppSelector } from "../../../../store/storeHelpers";
import { InfoTooltip } from "../../../graph/node-modal/editors/InfoTooltip/InfoTooltip";
import { AssertionExpression } from "./assertionResult/assertionExpression";
import { AssertionResult } from "./assertionResult/assertionResult";
import { AssertionResultMessage } from "./assertionResult/assertionResultMessage";
import { AssertionStatusIcon } from "./assertionResult/AssertionStatusIcon";

interface Props {
    nodeId: string;
}

export const AssertionResultsForNode = ({ nodeId }: Props) => {
    const testCaseAssertions = useAppSelector((state) => getTestCaseAssertionsForNode(state, nodeId));
    const assertionResults = useAppSelector((state) => getTestAssertionResultsForNode(state, nodeId));

    return (
        <Box display={"flex"} flexDirection={"column"} gap={0.25}>
            {testCaseAssertions.map((testCaseAssertion, index) => {
                const assertionResult = assertionResults?.[index];

                if (!assertionResult) {
                    return;
                }
                return (
                    <AssertionResult key={testCaseAssertion.uuid} assertionResult={assertionResult} testCaseAssertion={testCaseAssertion} />
                );
            })}
        </Box>
    );
};
