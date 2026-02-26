import { Box, Typography } from "@mui/material";
import React, {Fragment} from "react";

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

                const tooltipContent = (
                    <Box p={1}>
                        {testCaseAssertion.description && <AssertionExpression assertion={testCaseAssertion} />}
                        <AssertionResultMessage assertionResult={assertionResult} />
                    </Box>
                );

                const displayTooltip = assertionResult.type === "FailedAssertion" || testCaseAssertion.description;

                const content = (
                    <Box display={"flex"} gap={0.75} alignItems={"center"} sx={{ cursor: displayTooltip && "pointer" }}>
                        <AssertionStatusIcon isSuccess={assertionResult.type === "SuccessfulAssertion"} />
                        {testCaseAssertion.description ? (
                            <Typography variant={"caption"}>{testCaseAssertion.description}</Typography>
                        ) : (
                            <AssertionExpression assertion={testCaseAssertion} />
                        )}
                    </Box>
                );

                return (
                    <Fragment key={testCaseAssertion.uuid}>
                        {displayTooltip ? (
                            <InfoTooltip variant={"click"} title={tooltipContent} placement={"right"}>
                                {content}
                            </InfoTooltip>
                        ) : (
                            content
                        )}
                    </Fragment>
                );
            })}
        </Box>
    );
};
