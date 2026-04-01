import { calculateAssertionResultsSummary } from "../../../../../containers/assertions/assertionResultsUtils";
import { getNodeAssertionResults } from "../../../../../reducers/graph/testing";
import type { TestCaseResult } from "../../../../../reducers/graph/testing";
import type { AssertionStatus } from "../../../testCases/assertionResultsForNode/assertionResult/AssertionStatusIcon";

export const getAssertionResultsSummary = (testCaseResult: TestCaseResult | undefined): { status: AssertionStatus | null } => {
    if (testCaseResult?.status === "loading") return { status: "loading" };

    const nodeAssertionResults = getNodeAssertionResults(testCaseResult);
    if (nodeAssertionResults === undefined) return { status: null };

    const allResults = Object.values(nodeAssertionResults).flat();
    const { hasResult, failedCount } = calculateAssertionResultsSummary(allResults);

    if (!hasResult) return { status: "noAssertions" };
    return { status: failedCount === 0 ? "success" : "error" };
};
