import { createSelector } from "reselect";

import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import { safeParseExpression } from "../../components/modals/TestingDataRecords/utils";
import { getScenarioGraph } from "./graph";

const getNodeId = (_: unknown, nodeId: string) => nodeId;

export const getTestCase = createSelector(getScenarioGraph, ({ testCases }) => testCases.value);
export const getTestCaseOptions = createSelector(getTestCase, ({ name, id }) => [{ label: name, value: id }]);
export const getTestCaseAssertions = createSelector(getTestCase, ({ assertions }) => assertions);
export const getTestCaseAssertionsForNode = createSelector(
    getTestCaseAssertions,
    getNodeId,
    (assertions, nodeId) => assertions[nodeId] || [],
);

export const getTestCaseMocks = createSelector(getTestCase, ({ mocks }) => mocks);
export const getTestCaseMocksForNode = createSelector(getTestCaseMocks, getNodeId, (mocks, nodeId) => mocks[nodeId]);
export const getInputDataRecords = createSelector(getTestCase, ({ inputs }) => safeParseExpression<TestingDataRecords[]>(inputs) || []);

const getSourceId = (_: unknown, sourceId: string) => sourceId;
export const getInputDataRecordsForSingleSource = createSelector([getInputDataRecords, getSourceId], (testCaseInputs, sourceId: string) =>
    testCaseInputs.filter((r) => r.sourceId === sourceId),
);
export const hasInputDataRecordsDefined = createSelector(getInputDataRecords, (inputDataRecords) => inputDataRecords.length > 0);
