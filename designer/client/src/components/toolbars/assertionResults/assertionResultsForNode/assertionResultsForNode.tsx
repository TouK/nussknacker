import { Box } from "@mui/material";
import React from "react";

import { getTestCaseAssertionsForNode } from "../../../../reducers/selectors/testCases";
import { getTestAssertionResultsForNode } from "../../../../reducers/selectors/testing";
import { useAppSelector } from "../../../../store/storeHelpers";
import { AssertionResult } from "./assertionResult/assertionResult";

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
                    return null;
                }
                return (
                    <AssertionResult key={testCaseAssertion.uuid} assertionResult={assertionResult} testCaseAssertion={testCaseAssertion} />
                );
            })}
        </Box>
    );
};
