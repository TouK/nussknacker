import { Box, Typography } from "@mui/material";
import React from "react";

import { getTestCaseAssertionsForNode } from "../../../../reducers/selectors/testCases";
import { getTestAssertionResultsForNode } from "../../../../reducers/selectors/testing";
import { useAppSelector } from "../../../../store/storeHelpers";
import { InfoTooltip } from "../../../graph/node-modal/editors/InfoTooltip/InfoTooltip";
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
        <Box display={"flex"} flexDirection={"column"} gap={0.25}>
            {testCaseAssertions.map((testCaseAssertion, index) => {
                const assertionResult = assertionResults?.[index];
                if (!assertionResult) {
                    return (
                        <Box key={testCaseAssertion.uuid}>
                            <Box display={"flex"} gap={0.75} alignItems={"center"}>
                                <AssertionExpression assertion={testCaseAssertion} />
                            </Box>
                        </Box>
                    );
                }

                const tooltipContent = (
                    <Box p={1}>
                        {testCaseAssertion.description && <AssertionExpression assertion={testCaseAssertion} />}
                        <AssertionResultMessage assertionResult={assertionResult} />
                    </Box>
                );

                return (
                    <InfoTooltip key={testCaseAssertion.uuid} variant={"click"} title={tooltipContent}>
                        <Box display={"flex"} gap={0.75} alignItems={"center"} sx={{ cursor: "pointer" }}>
                            <AssertionStatusIcon isSuccess={assertionResult.type === "SuccessfulAssertion"} />
                            {testCaseAssertion.description ? (
                                <Typography variant={"caption"}>{testCaseAssertion.description}</Typography>
                            ) : (
                                <AssertionExpression assertion={testCaseAssertion} />
                            )}
                        </Box>
                    </InfoTooltip>
                );
            })}
        </Box>
    );
};
