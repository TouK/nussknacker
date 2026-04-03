import type { WithUuid } from "../../components/graph/node-modal/appendUuid";
import type { ExpressionObj } from "../../components/graph/node-modal/editors/expression/types";
import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import type { TestCase } from "../../reducers/graph/testCase";
import {
    getTestCaseAssertions,
    getTestCaseAssertionsForNode,
    getTestCases,
    getTestData,
    getTestCaseMocks,
} from "../../reducers/selectors/testCases";
import { getActiveTestCaseId } from "../../reducers/selectors/testing";
import type { ThunkAction } from "../reduxTypes";
import { changeActiveTestCase } from "./testingActions";

export type AssertionOperator =
    | "equals"
    | "notEquals"
    | "greaterThan"
    | "lessThan"
    | "greaterThanOrEqual"
    | "lessThanOrEqual"
    | "hasSize"
    | "contains"
    | "matches";
export type Assertion = { description?: string; expected: ExpressionObj; operator: AssertionOperator; actual: ExpressionObj };
export type Assertions = Record<string, Assertion[]>;

export type Mock = { expression: ExpressionObj };
export type Mocks = Record<string, Mock>;

export type TestCasesActions =
    | { type: "UPDATE_TEST_CASE"; testCaseId: string; updates: Omit<Partial<TestCase>, "id"> }
    | { type: "ADD_TEST_CASE"; testCase: TestCase }
    | { type: "REMOVE_TEST_CASE"; testCaseId: string };

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

export function setTestCaseName(name: string): ThunkAction {
    return (dispatch, getState) => {
        const activeTestCaseId = getActiveTestCaseId(getState());
        dispatch({
            type: "UPDATE_TEST_CASE",
            testCaseId: activeTestCaseId,
            updates: { name },
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

export function addTestCase(testCase: TestCase): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "ADD_TEST_CASE",
            testCase,
        });
        dispatch(changeActiveTestCase(testCase.id));
    };
}

export function removeTestCase(testCaseId: string): ThunkAction {
    return (dispatch, getState) => {
        const testCases = getTestCases(getState());
        const deletedIndex = testCases.findIndex((tc) => tc.id === testCaseId);
        const remaining = testCases.filter((tc) => tc.id !== testCaseId);
        const nextActive = remaining[deletedIndex] ?? remaining[deletedIndex - 1];

        dispatch({ type: "REMOVE_TEST_CASE", testCaseId });

        if (nextActive) {
            dispatch(changeActiveTestCase(nextActive.id));
        }
    };
}
