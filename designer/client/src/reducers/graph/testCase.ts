import { produce } from "immer";

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
    list: [
        {
            id: "f8757b06-6610-4900-90cc-fd3963356e8e",
            name: "Test case 1",
            inputs: "[]",
            mocks: {},
            assertions: {},
        },
        {
            id: "f8757b06-6610-4900-90cc-fd3963356e11",
            name: "Test case 2",
            inputs: "[]",
            mocks: {},
            assertions: {},
        },
    ],
};

export const testCaseReducer: Reducer<ScenarioGraph["testCases"]> = produce((draft, action) => {
    switch (action.type) {
        case "UPDATE_TEST_CASE": {
            const testCase = draft.list.find((tc) => tc.id === action.testCaseId);
            if (!testCase) return;

            Object.assign(testCase, action.updates);

            break;
        }

        case "DELETE_NODES":
            return {
                ...draft,
                list: cleanTestCaseState(draft, action.ids),
            };
        case "ADD_NODE_REPLACE":
            return {
                ...draft,
                list: cleanTestCaseState(draft, [action.old.id]),
            };
        default:
            return draft;
    }
});

const cleanTestCaseState = (state: ScenarioGraph["testCases"], ids: string[]) => {
    return state.list.map((testCase) => ({
        ...testCase,
        assertions: omit(testCase.assertions, ids),
        mocks: omit(testCase.mocks, ids),
        inputs: JSON.stringify(
            safeParseExpression<TestingDataRecords[]>(testCase.inputs)?.filter((input) => !ids.includes(input.sourceId)),
        ),
    }));
};
