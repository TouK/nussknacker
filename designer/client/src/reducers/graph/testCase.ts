import { v4 as uuid4 } from "uuid";

import type { Assertions, Mocks } from "../../actions/nk/testCasesActions";
import type { Reducer } from "../../actions/reduxTypes";
import type { ScenarioGraph } from "../../types/scenarioGraph";

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
        default:
            return state;
    }
};
