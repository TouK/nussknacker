import type { Reducer } from "../../actions/reduxTypes";
import type { SourceWithParametersTest } from "../../http/HttpService/types";
import type { MultipleResultsWithCountsDto, NodeAssertionResults, TestResultsDto } from "../../http/resultsWithCountsDto";
import type { GraphState } from "./types";
type Source = SourceWithParametersTest["sourceId"];
export type SourceTestData = SourceWithParametersTest["parameterExpressions"];
export type TestData = Record<Source, SourceTestData>;

export type TestingState = {
    testResults: TestResultsDto;
    testCasesResults: TestCasesResults;
    testResultsLoading?: boolean;
    testData?: TestData;
    activeTestCaseId?: string;
};

export const initialTestingState: GraphState["testing"] = {
    testData: null,
    testResults: null,
    testCasesResults: null,
};

export type TestCaseResult = { status: "loading" } | { status: "loaded"; results: MultipleResultsWithCountsDto };

export type TestCasesResults = Record<string, TestCaseResult>;

export function getNodeAssertionResults(testCaseResult: TestCaseResult | undefined): NodeAssertionResults | undefined {
    return testCaseResult?.status === "loaded" && testCaseResult.results.type === "Completed"
        ? testCaseResult.results.result.assertionsResults
        : undefined;
}

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
                testCasesResults: {
                    ...state.testCasesResults,
                    [action.testCaseId]: { status: "loading" },
                },
            };
        }
        case "DISPLAY_TEST_ASSERTIONS_RESULTS": {
            return {
                ...state,
                testCasesResults: {
                    ...state.testCasesResults,
                    [action.testCaseId]: { status: "loaded", results: action.results },
                },
            };
        }
        case "CLEAR_TEST_ASSERTIONS_RESULTS": {
            return {
                ...state,
                testCasesResults: null,
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
        case "TEST_CASE_ASSERTION_RESULTS_FAILED": {
            const { [action.testCaseId]: _, ...remainingTestCasesResults } = state.testCasesResults ?? {};
            return {
                ...state,
                testCasesResults: remainingTestCasesResults,
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
