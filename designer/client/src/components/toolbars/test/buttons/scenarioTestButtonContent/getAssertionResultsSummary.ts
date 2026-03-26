import { calculateAssertionResultsSummary } from "../../../../../containers/assertions/assertionResultsUtils";
import type { TestCaseAssertionResult } from "../../../../../http/resultsWithCountsDto";

export const getAssertionResultsSummary = (testCaseAssertionResult: TestCaseAssertionResult | undefined) => {
    const allResults = testCaseAssertionResult?.status === "loaded" ? Object.values(testCaseAssertionResult.results).flat() : [];
    const { hasResult, failedCount } = calculateAssertionResultsSummary(allResults);
    const assertionsIsSuccess = hasResult && failedCount === 0;

    return { hasResult, assertionsIsSuccess };
};
