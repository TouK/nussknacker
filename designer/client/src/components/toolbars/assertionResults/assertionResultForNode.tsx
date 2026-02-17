import React from "react";

import { getTestCaseAssertionsForNode } from "../../../reducers/selectors/testCases";
import { getTestAssertionResultsForNode } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
import { AssertionExpression } from "./assertionExpression";
import { AssertionStatus } from "./assertionStatus";

interface Props {
    nodeId: string;
}
export const AssertionResultForNode = ({ nodeId }: Props) => {
    const testCaseAssertions = useAppSelector((state) => getTestCaseAssertionsForNode(state, nodeId));
    const assertionResults = useAppSelector((state) => getTestAssertionResultsForNode(state, nodeId));

    return (
        <div>
            {testCaseAssertions.map((testCaseAssertion, index) => (
                <>
                    <AssertionExpression assertion={testCaseAssertion} />
                    <AssertionStatus assertionResult={assertionResults[index]} />
                </>
            ))}
        </div>
    );
};
