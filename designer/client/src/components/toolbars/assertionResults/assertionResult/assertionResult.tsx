import { Box } from "@mui/material";
import React from "react";

import { getTestCaseAssertionsForNode } from "../../../../reducers/selectors/testCases";
import { getTestAssertionResultsForNode } from "../../../../reducers/selectors/testing";
import { useAppSelector } from "../../../../store/storeHelpers";
import { AssertionExpression } from "./assertionExpression";
import { AssertionResultMessage } from "./assertionResultMessage";
import { AssertionStatusIcon } from "./AssertionStatusIcon";

interface Props {
    nodeId: string;
}
export const AssertionResult = ({ nodeId }: Props) => {
    const testCaseAssertions = useAppSelector((state) => getTestCaseAssertionsForNode(state, nodeId));
    const assertionResults = useAppSelector((state) => getTestAssertionResultsForNode(state, nodeId));

    return (
        <Box display={"flex"} flexDirection={"column"} gap={1}>
            {testCaseAssertions.map((testCaseAssertion, index) => {
                const assertionResult = assertionResults?.[index];
                if (!assertionResult)
                    return (
                        <Box key={index}>
                            <Box display={"flex"} gap={0.75} alignItems={"center"}>
                                <AssertionExpression assertion={testCaseAssertion} />
                            </Box>
                        </Box>
                    );

                return (
                    <Box key={index}>
                        <Box display={"flex"} gap={0.75} alignItems={"center"}>
                            <AssertionStatusIcon isSuccess={assertionResult.type === "SuccessfulAssertion"} />
                            <AssertionExpression assertion={testCaseAssertion} />
                        </Box>
                        <AssertionResultMessage assertionResult={assertionResult} />
                    </Box>
                );
            })}
        </Box>
    );
};
