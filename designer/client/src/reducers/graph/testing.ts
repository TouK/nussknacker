import type { Reducer } from "../../actions/reduxTypes";
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
    activeTestCaseId?: string;
};

export const initialTestingState: GraphState["testing"] = {
    testData: null,
    testResults: null,
    assertionsResults: null,
};

export const testingReducer: Reducer<GraphState["testing"]> = (state = initialTestingState, action) => {
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
        case "SET_TEST_CASE_ASSERTION_RESULTS_LOADING": {
            return {
                ...state,
                assertionsResults: {
                    ...state.assertionsResults,
                    [action.testCaseId]: { status: "loading" },
                },
            };
        }
        case "DISPLAY_TEST_ASSERTIONS_RESULTS": {
            return {
                ...state,
                assertionsResults: {
                    ...state.assertionsResults,
                    [action.testCaseId]: { status: "loaded", results: action.assertionsResults },
                },
            };
        }
        case "CLEAR_TEST_ASSERTIONS_RESULTS": {
            return {
                ...state,
                assertionsResults: null,
            };
        }
        case "TEST_RESULTS_LOADING": {
            return {
                ...state,
                testResultsLoading: true,
            };
        }
        case "TEST_RESULTS_FAILED": {
            return {
                ...state,
                testResultsLoading: false,
            };
        }
        case "DISPLAY_PROCESS_COUNTS": {
            return {
                ...state,
                testResults: null,
            };
        }
        case "DISPLAY_LIVE_DATA": {
            return {
                ...state,
                testResults: action.results?.results || null,
            };
        }
        case "CHANGE_ACTIVE_TEST_CASE": {
            return {
                ...state,
                testResults: null,
                activeTestCaseId: action.testCaseId,
            };
        }
        default:
            return state;
    }
};
