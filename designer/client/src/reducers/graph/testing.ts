import type { Reducer } from "../../actions/reduxTypes";
import type { ExpressionObj } from "../../components/graph/node-modal/editors/expression/types";
import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import type { SourceWithParametersTest } from "../../http/HttpService/types";
import type { TestAssertionResults, TestResultsDto } from "../../http/resultsWithCountsDto";
import type { GraphState } from "./types";

type Source = SourceWithParametersTest["sourceId"];
export type SourceTestData = SourceWithParametersTest["parameterExpressions"];
export type TestData = Record<Source, SourceTestData>;

export type TestingState = {
    testResults: TestResultsDto;
    assertionsResults: TestAssertionResults;
    testResultsLoading?: boolean;
    testData?: TestData;
    testingDataRecords?: TestingDataRecords[];
    testingAssertions?: Record<string, { expression: ExpressionObj }[]>;
};

export const initialTestCasesState: GraphState["testing"] = {
    testData: null,
    testResults: null,
    assertionsResults: null,
};

export const testingReducer: Reducer<GraphState["testing"]> = (state = initialTestCasesState, action) => {
    switch (action.type) {
        case "DISPLAY_TEST_RESULTS_DETAILS": {
            return {
                ...state,
                testData: {
                    ...state.testData,
                    [action.testData?.sourceId]: action.testData?.parameterExpressions,
                },
                testResultsLoading: false,
                testResults: action.testResults,
            };
        }
        case "DISPLAY_TEST_ASSERTIONS_RESULTS": {
            return {
                ...state,
                assertionsResults: action.assertionsResults,
            };
        }
        case "CLEAR_TEST_ASSERTIONS_RESULTS": {
            return {
                ...state,
                assertionsResults: null,
            };
        }
        default:
            return state;
    }
};
