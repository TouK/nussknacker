import { v4 as uuid4 } from "uuid";

import type { Reducer } from "../../actions/reduxTypes";
import type { ExpressionObj } from "../../components/graph/node-modal/editors/expression/types";
import type { ScenarioGraph } from "../../types/scenarioGraph";

type Mocks = Record<string, { expression: ExpressionObj }>;
type Assertions = Record<string, { expression: ExpressionObj }[]>;

export interface TestCase {
    id: string;
    name: string;
    inputs: string;
    mocks: Mocks;
    assertions: Assertions;
}

export const initialTestCasesState: ScenarioGraph["testCases"] = {
    id: uuid4(),
    name: "Test case 1",
    inputs: "[]",
    mocks: {},
    assertions: {},
};

export const testCaseReducer: Reducer<ScenarioGraph["testCases"]> = (state = initialTestCasesState, action) => {
    switch (action.type) {
        case "TEST_CASE_UPDATE":
            return {
                ...state,
                testCases: action.testCases,
            };
        case "SET_TEST_CASE_ASSERTIONS":
            return {
                ...state,
                assertions: action.assertions,
            };
        case "SET_TEST_CASE_INPUTS":
            return {
                ...state,
                inputs: action.inputs,
            };
        default:
            return state;
    }
};
