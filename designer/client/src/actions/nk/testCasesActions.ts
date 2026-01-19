import type { WithUuid } from "../../components/graph/node-modal/appendUuid";
import type { ExpressionObj } from "../../components/graph/node-modal/editors/expression/types";
import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import {
    getTestCaseAssertions,
    getTestCaseAssertionsForNode,
    getInputDataRecords,
    getTestCaseMocks,
} from "../../reducers/selectors/testCases";
import type { ThunkAction } from "../reduxTypes";

export type Assertion = { expression: ExpressionObj };
export type Assertions = Record<string, Assertion[]>;

export type Mock = { expression: ExpressionObj };
export type Mocks = Record<string, Mock>;

export type TestCasesActions =
    | { type: "SET_TEST_CASE_ASSERTIONS"; assertions: Assertions }
    | { type: "SET_TEST_CASE_INPUTS"; inputs: string }
    | { type: "SET_TEST_CASE_MOCKS"; mocks: Mocks };

export function setTestCaseAssertions(nodeId: string, updater: (prev: WithUuid<Assertion>[]) => WithUuid<Assertion>[]): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const prev = getTestCaseAssertionsForNode(state, nodeId);
        const next = updater(prev);

        const testingAssertions = getTestCaseAssertions(state);
        dispatch({
            type: "SET_TEST_CASE_ASSERTIONS",
            assertions: { ...testingAssertions, [nodeId]: next },
        });
    };
}

export function setTestCaseInputs(updater: (prev: TestingDataRecords[]) => TestingDataRecords[]): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const prev = getInputDataRecords(state);
        const next = updater(prev);

        dispatch({
            type: "SET_TEST_CASE_INPUTS",
            inputs: JSON.stringify(next),
        });
    };
}

export function setTestCaseMock(nodeId: string, expression: ExpressionObj): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();

        const mocks = getTestCaseMocks(state);
        dispatch({ type: "CLEAR_TEST_ASSERTIONS_RESULTS" });
        dispatch({
            type: "SET_TEST_CASE_MOCKS",
            mocks: { ...mocks, [nodeId]: { expression } },
        });
    };
}
