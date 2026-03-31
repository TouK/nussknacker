import { calculateAssertionResultsSummary } from "../../../../../containers/assertions/assertionResultsUtils";
import type { TestCaseResult } from "../../../../../reducers/graph/testing";

export const getAssertionResultsSummary = (testCaseResult: TestCaseResult | undefined) => {
    const nodeAssertionResults =
        testCaseResult?.status === "loaded" && testCaseResult.results.type === "Completed"
            ? testCaseResult.results.result.assertionsResults
            : null;
    const allResults = nodeAssertionResults ? Object.values(nodeAssertionResults).flat() : [];
    const { hasResult, failedCount } = calculateAssertionResultsSummary(allResults);
    const assertionsIsSuccess = hasResult && failedCount === 0;

    return { hasResult, assertionsIsSuccess };
};
