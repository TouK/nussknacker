import { isEmpty } from "lodash";
import { createSelector } from "reselect";

import type { TestData, TestingState } from "../graph/testing";
import { getGraph, getProcessCounts } from "./graph";

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

export const getActiveTestCaseAssertionResult = createSelector(
    getTestAssertionResults,
    getActiveTestCaseId,
    (assertionsResults, testCaseId) => (testCaseId ? assertionsResults[testCaseId] : undefined),
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
