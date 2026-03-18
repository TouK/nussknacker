import type { WithUuid } from "../../components/graph/node-modal/appendUuid";
import type { ExpressionObj } from "../../components/graph/node-modal/editors/expression/types";
import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import type { TestCase } from "../../reducers/graph/testCase";
import { getTestCaseAssertions, getTestCaseAssertionsForNode, getTestData, getTestCaseMocks } from "../../reducers/selectors/testCases";
import { getActiveTestCaseId } from "../../reducers/selectors/testing";
import type { ThunkAction } from "../reduxTypes";

export type Assertion = { description?: string; expected: ExpressionObj; operator: "equals" | "notEquals"; actual: ExpressionObj };
export type Assertions = Record<string, Assertion[]>;

export type Mock = { expression: ExpressionObj };
export type Mocks = Record<string, Mock>;

export type TestCasesActions = { type: "UPDATE_TEST_CASE"; testCaseId: string; updates: Partial<TestCase> };

export function setTestCaseAssertions(nodeId: string, updater: (prev: WithUuid<Assertion>[]) => WithUuid<Assertion>[]): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const prev = getTestCaseAssertionsForNode(state, nodeId);
        const next = updater(prev);

        const testingAssertions = getTestCaseAssertions(state);
        const activeTestCaseId = getActiveTestCaseId(state);

        dispatch({
            type: "UPDATE_TEST_CASE",
            testCaseId: activeTestCaseId,
            updates: { assertions: { ...testingAssertions, [nodeId]: next } },
        });
    };
}

export function setTestCaseInputs(updater: (prev: TestingDataRecords[]) => TestingDataRecords[]): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const prev = getTestData(state);
        const next = updater(prev);
        const activeTestCaseId = getActiveTestCaseId(state);

        dispatch({
            type: "UPDATE_TEST_CASE",
            testCaseId: activeTestCaseId,
            updates: {
                inputs: JSON.stringify(next),
            },
        });
    };
}

export function setTestCaseMock(nodeId: string, expression: ExpressionObj | undefined): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();

        const activeTestCaseId = getActiveTestCaseId(state);

        const { [nodeId]: _, ...remainingMocks } = getTestCaseMocks(state);

        if (expression?.expression) {
            dispatch({
                type: "UPDATE_TEST_CASE",
                testCaseId: activeTestCaseId,
                updates: { mocks: { ...remainingMocks, [nodeId]: { expression } } },
            });
        } else {
            dispatch({ type: "UPDATE_TEST_CASE", testCaseId: activeTestCaseId, updates: { mocks: remainingMocks } });
        }
    };
}
