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

function cleanTestCaseResultOfNodes(testCaseResult: TestCaseResult, deletedIds: Set<string>): TestCaseResult {
    if (testCaseResult.status !== "loaded" || testCaseResult.results.type !== "Completed") {
        return testCaseResult;
    }
    const { assertionsResults } = testCaseResult.results.result;
    const updatedAssertionsResults = Object.fromEntries(Object.entries(assertionsResults).filter(([nodeId]) => !deletedIds.has(nodeId)));
    return {
        ...testCaseResult,
        results: {
            ...testCaseResult.results,
            result: { ...testCaseResult.results.result, assertionsResults: updatedAssertionsResults },
        },
    };
}

function cleanTestCasesResultsOfNodes(testCasesResults: TestCasesResults | null, nodeIds: string[]): TestCasesResults | null {
    if (!testCasesResults) return null;
    const deletedIds = new Set(nodeIds);
    return Object.fromEntries(
        Object.entries(testCasesResults).map(([testCaseId, testCaseResult]) => [
            testCaseId,
            cleanTestCaseResultOfNodes(testCaseResult, deletedIds),
        ]),
    );
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
        case "CLEAR_TEST_CASE_NODE_ASSERTION_RESULTS": {
            const testCaseResult = state.testCasesResults?.[action.testCaseId];
            if (testCaseResult?.status !== "loaded" || testCaseResult.results.type !== "Completed") {
                return state;
            }
            const nodeResults = testCaseResult.results.result.assertionsResults[action.nodeId];
            if (!nodeResults) {
                return state;
            }
            const updatedNodeResults = nodeResults.filter((_, i) => i !== action.assertionIndex);
            return {
                ...state,
                testCasesResults: {
                    ...state.testCasesResults,
                    [action.testCaseId]: {
                        ...testCaseResult,
                        results: {
                            ...testCaseResult.results,
                            result: {
                                ...testCaseResult.results.result,
                                assertionsResults: {
                                    ...testCaseResult.results.result.assertionsResults,
                                    [action.nodeId]: updatedNodeResults,
                                },
                            },
                        },
                    },
                },
            };
        }
        case "DELETE_NODES": {
            return { ...state, testCasesResults: cleanTestCasesResultsOfNodes(state.testCasesResults, action.ids) };
        }
        case "ADD_NODE_REPLACE": {
            return { ...state, testCasesResults: cleanTestCasesResultsOfNodes(state.testCasesResults, [action.old.id]) };
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
