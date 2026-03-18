import { isEmpty } from "lodash";
import { createSelector } from "reselect";

import type { TestData, TestingState } from "../graph/testing";
import { getGraph, getProcessCounts } from "./graph";

export const getTesting = createSelector(getGraph, (g) => g.testing || ({} as TestingState));

export const getTestResults = createSelector(getTesting, (g) => g.testResults);
export const getTestAssertionResults = createSelector(getTesting, (g) => g.assertionsResults || {});

const getNodeId = (_: unknown, nodeId: string) => nodeId;
export const getTestAssertionResultsForNode = createSelector(
    getTestAssertionResults,
    getNodeId,
    (assertionsResults, nodeId) => assertionsResults[nodeId],
);
export const getTestResultsLoading = createSelector(getTesting, (g) => g.testResultsLoading);
export const getTestData = createSelector(getTesting, (g) => g.testData || ({} as TestData));
export const getIsTestingMode = createSelector(
    getTestResults,
    getProcessCounts,
    (results, counts) => !isEmpty(results) || !isEmpty(counts),
);

export const getActiveTestCaseId = createSelector(getTesting, (g) => g.activeTestCaseId);
