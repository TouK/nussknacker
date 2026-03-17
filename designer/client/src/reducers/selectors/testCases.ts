import { createSelector } from "reselect";

import type { Assertions, Mocks } from "../../actions/nk/testCasesActions";
import { withUuid } from "../../components/graph/node-modal/appendUuid";
import { MockExpressionParameter } from "../../components/graph/node-modal/editors/expression/MockExpressionField";
import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import { safeParseExpression } from "../../components/modals/TestingDataRecords/utils";
import { getScenarioGraph } from "./graph";
import { getActiveTestCaseId } from "./testing";

const getNodeId = (_: unknown, nodeId: string) => nodeId;

export const getTestCases = createSelector(getScenarioGraph, ({ testCases }) => testCases?.list ?? []);
export const getActiveTestCase = createSelector(getTestCases, getActiveTestCaseId, (testCases, activeTestCaseId) =>
    testCases.find((testCase) => testCase.id === activeTestCaseId),
);

export const getTestCaseOptions = createSelector(getTestCases, (testCases) =>
    testCases.map(({ id, name }) => ({ label: name, value: id })),
);
export const getActiveTestCaseOption = createSelector(
    getTestCaseOptions,
    getActiveTestCaseId,
    (testCaseOptions, activeTestCaseId) => testCaseOptions.find((option) => option.value === activeTestCaseId) || null,
);
export const getTestCaseAssertions = createSelector(getActiveTestCase, (testCase) => testCase?.assertions ?? ({} as Assertions));
export const getTestCaseAssertionsForNode = createSelector(
    getTestCaseAssertions,
    getNodeId,
    (assertions, nodeId) => assertions[nodeId]?.map(withUuid) || [],
);

export const getTestCaseMocks = createSelector(getActiveTestCase, (testCase) => testCase?.mocks ?? ({} as Mocks));
export const getTestCaseMockForNode = createSelector(
    getTestCaseMocks,
    getNodeId,
    (mocks, nodeId) => mocks[nodeId] || { expression: MockExpressionParameter.defaultValue },
);
export const getInputDataRecords = createSelector(
    getActiveTestCase,
    (testCase) => safeParseExpression<TestingDataRecords[]>(testCase?.inputs) || [],
);

const getSourceId = (_: unknown, sourceId: string) => sourceId;
export const getInputDataRecordsForSingleSource = createSelector([getInputDataRecords, getSourceId], (testCaseInputs, sourceId: string) =>
    testCaseInputs.filter((r) => r.sourceId === sourceId),
);
export const hasInputDataRecordsDefined = createSelector(getInputDataRecords, (inputDataRecords) => inputDataRecords.length > 0);

export const getTestCaseNodeValidationData = createSelector(getActiveTestCase, getNodeId, (testCase, nodeId) => {
    if (!testCase) return {};

    return {
        [testCase.name]: {
            ...testCase,
            assertions: testCase.assertions[nodeId] ?? [],
            enricherMock: testCase.mocks[nodeId],
        },
    };
});
