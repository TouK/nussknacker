import { calculateAssertionResultsSummary } from "../../../../../containers/assertions/assertionResultsUtils";
import { getNodeAssertionResults } from "../../../../../reducers/graph/testing";
import type { TestCaseResult } from "../../../../../reducers/graph/testing";

export const getAssertionResultsSummary = (testCaseResult: TestCaseResult | undefined) => {
    const nodeAssertionResults = getNodeAssertionResults(testCaseResult);
    const allResults = nodeAssertionResults ? Object.values(nodeAssertionResults).flat() : [];
    const { hasResult, failedCount } = calculateAssertionResultsSummary(allResults);
    const assertionsIsSuccess = hasResult && failedCount === 0;

    return { hasResult, assertionsIsSuccess };
};
