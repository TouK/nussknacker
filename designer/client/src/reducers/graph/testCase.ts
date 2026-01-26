import type { Assertions, Mocks } from "../../actions/nk/testCasesActions";
import type { Reducer } from "../../actions/reduxTypes";
import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import { safeParseExpression } from "../../components/modals/TestingDataRecords/utils";
import type { ScenarioGraph } from "../../types/scenarioGraph";
import { omit } from "./lodashWrappers";

export interface TestCase {
    id: string;
    name: string;
    inputs: string;
    mocks: Mocks;
    assertions: Assertions;
}

export const initialTestCasesState: ScenarioGraph["testCases"] = {
    value: {
        id: "f8757b06-6610-4900-90cc-fd3963356e8e",
        name: "Test case 1",
        inputs: "[]",
        mocks: {},
        assertions: {},
    },
};

export const testCaseReducer: Reducer<ScenarioGraph["testCases"]> = (state = initialTestCasesState, action) => {
    switch (action.type) {
        case "SET_TEST_CASE_ASSERTIONS":
            return {
                ...state,
                value: {
                    ...state.value,
                    assertions: action.assertions,
                },
            };
        case "SET_TEST_CASE_INPUTS":
            return {
                ...state,
                value: {
                    ...state.value,
                    inputs: action.inputs,
                },
            };
        case "SET_TEST_CASE_MOCKS":
            return {
                ...state,
                value: {
                    ...state.value,
                    mocks: action.mocks,
                },
            };
        case "DELETE_NODES":
            return {
                ...state,
                value: cleanTestCaseState(state, action.ids),
            };
        case "ADD_NODE_REPLACE":
            return {
                ...state,
                value: cleanTestCaseState(state, [action.old.id]),
            };
        default:
            return state;
    }
};

const cleanTestCaseState = (state: ScenarioGraph["testCases"], ids: string[]) => {
    return {
        ...state.value,
        assertions: omit(state.value.assertions, ids),
        mocks: omit(state.value.mocks, ids),
        inputs: JSON.stringify(
            safeParseExpression<TestingDataRecords[]>(state.value.inputs)?.filter((input) => !ids.includes(input.sourceId)),
        ),
    };
};
