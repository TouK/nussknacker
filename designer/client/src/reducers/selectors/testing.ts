import { isEmpty } from "lodash";
import { createSelector } from "reselect";

import type { TestCaseResult, TestCasesResults, TestData, TestingState } from "../graph/testing";
import { getGraph, getNodes, getProcessCounts } from "./graph";

export const getTesting = createSelector(getGraph, (g) => g.testing || ({} as TestingState));

export const getTestResults = createSelector(getTesting, (g) => g.testResults);
export const getTestAssertionResults = createSelector(getTesting, (g) => g.testCasesResults || {});
export const getTestResultsLoading = createSelector(getTesting, (g) => g.testResultsLoading);
export const getTestData = createSelector(getTesting, (g) => g.testData || ({} as TestData));
export const getIsTestingMode = createSelector(
    getTestResults,
    getProcessCounts,
    (results, counts) => !isEmpty(results) || !isEmpty(counts),
);
export const getActiveTestCaseId = createSelector(getTesting, (g) => g.activeTestCaseId);

function filterTestCaseResultByExistingNodes(testCaseResult: TestCaseResult, nodeIds: Set<string>): TestCaseResult {
    if (testCaseResult.status !== "loaded" || testCaseResult.results.type !== "Completed") {
        return testCaseResult;
    }
    const { assertionsResults } = testCaseResult.results.result;
    const filteredAssertionsResults = Object.fromEntries(Object.entries(assertionsResults).filter(([nodeId]) => nodeIds.has(nodeId)));
    return {
        ...testCaseResult,
        results: {
            ...testCaseResult.results,
            result: { ...testCaseResult.results.result, assertionsResults: filteredAssertionsResults },
        },
    };
}

export const getFilteredTestAssertionResults = createSelector(
    getTestAssertionResults,
    getNodes,
    (assertionsResults, nodes): TestCasesResults => {
        const nodeIds = new Set(nodes.map((n) => n.id));
        return Object.fromEntries(
            Object.entries(assertionsResults).map(([testCaseId, testCaseResult]) => [
                testCaseId,
                filterTestCaseResultByExistingNodes(testCaseResult, nodeIds),
            ]),
        );
    },
);

export const getActiveTestCaseAssertionResult = createSelector(
    getFilteredTestAssertionResults,
    getActiveTestCaseId,
    (assertionsResults, testCaseId): TestCaseResult | undefined => (testCaseId ? assertionsResults[testCaseId] : undefined),
);

const getNodeId = (_: unknown, nodeId: string) => nodeId;
export const getTestAssertionResultsForNode = createSelector(getActiveTestCaseAssertionResult, getNodeId, (testCaseResult, nodeId) =>
    testCaseResult?.status === "loaded" && testCaseResult.results.type === "Completed"
        ? testCaseResult.results.result.assertionsResults[nodeId]
        : undefined,
);

export const getActiveTestCaseAssertionResultLoading = createSelector(
    getActiveTestCaseAssertionResult,
    (testCaseResult) => testCaseResult?.status === "loading",
);
