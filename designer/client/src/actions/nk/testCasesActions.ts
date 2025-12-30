import type { ExpressionObj } from "../../components/graph/node-modal/editors/expression/types";
import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import type { TestCase } from "../../reducers/graph/testCase";
import { getTestCaseAssertions, getTestCaseAssertionsForNode, getTestDataRecords } from "../../reducers/selectors/testCases";
import type { ThunkAction } from "../reduxTypes";

type Assertion = { expression: ExpressionObj };
type Assertions = Record<string, Assertion[]>;

export type TestCasesActions =
    | { type: "TEST_CASE_UPDATE"; testCases: TestCase }
    | { type: "SET_TEST_CASE_ASSERTIONS"; assertions: Assertions }
    | { type: "SET_TEST_CASE_INPUTS"; inputs: string };

export function setTestCaseAssertions(nodeId: string, updater: (prev: Assertion[]) => Assertion[]): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const prev = getTestCaseAssertionsForNode(state, nodeId);
        const next = updater(prev);

        const testingAssertions = getTestCaseAssertions(state);
        dispatch({ type: "CLEAR_TEST_ASSERTIONS_RESULTS" });
        dispatch({
            type: "SET_TEST_CASE_ASSERTIONS",
            assertions: { ...testingAssertions, [nodeId]: next },
        });
    };
}

export function setTestCaseInputs(updater: (prev: TestingDataRecords[]) => TestingDataRecords[]): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const prev = getTestDataRecords(state);
        const next = updater(prev);

        dispatch({
            type: "SET_TEST_CASE_INPUTS",
            inputs: JSON.stringify(next),
        });
    };
}
