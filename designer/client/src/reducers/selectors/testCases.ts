import { createSelector } from "reselect";

import { withUuid } from "../../components/graph/node-modal/appendUuid";
import { MockExpressionParameter } from "../../components/graph/node-modal/editors/expression/MockExpressionField";
import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import { safeParseExpression } from "../../components/modals/TestingDataRecords/utils";
import type { TestCase } from "../graph/testCase";
import { getScenarioGraph } from "./graph";

const getNodeId = (_: unknown, nodeId: string) => nodeId;

export const getTestCase = createSelector(getScenarioGraph, ({ testCases }) => testCases?.value ?? ({} as TestCase));
export const getTestCaseOptions = createSelector(getTestCase, ({ name, id }) => [{ label: name, value: id }]);
export const getTestCaseAssertions = createSelector(getTestCase, ({ assertions }) => assertions);
export const getTestCaseAssertionsForNode = createSelector(
    getTestCaseAssertions,
    getNodeId,
    (assertions, nodeId) => assertions[nodeId]?.map(withUuid) || [],
);

export const getTestCaseMocks = createSelector(getTestCase, ({ mocks }) => mocks);
export const getTestCaseMockForNode = createSelector(
    getTestCaseMocks,
    getNodeId,
    (mocks, nodeId) => mocks[nodeId] || { expression: MockExpressionParameter.defaultValue },
);
export const getInputDataRecords = createSelector(getTestCase, ({ inputs }) => safeParseExpression<TestingDataRecords[]>(inputs) || []);

const getSourceId = (_: unknown, sourceId: string) => sourceId;
export const getInputDataRecordsForSingleSource = createSelector([getInputDataRecords, getSourceId], (testCaseInputs, sourceId: string) =>
    testCaseInputs.filter((r) => r.sourceId === sourceId),
);
export const hasInputDataRecordsDefined = createSelector(getInputDataRecords, (inputDataRecords) => inputDataRecords.length > 0);

export const getTestCaseNodeValidationData = createSelector(getScenarioGraph, getNodeId, ({ testCases }, nodeId) => {
    const testCase = testCases?.value ?? ({} as TestCase);

    return {
        [testCase.name]: {
            ...testCase,
            assertions: testCase.assertions[nodeId] ?? [],
            enricherMock: testCase.mocks[nodeId],
        },
    };
});
