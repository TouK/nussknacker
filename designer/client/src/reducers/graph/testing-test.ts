import type { MultipleResultsWithCountsDto } from "../../http/resultsWithCountsDto";
import { getNodeAssertionResults, initialTestingState, testingReducer } from "./testing";
import type { TestCaseResult } from "./testing";

const completedResult = (assertionsResults = {}): MultipleResultsWithCountsDto => ({
    type: "Completed",
    testCaseId: "tc-1",
    testCaseName: "TC1",
    result: {
        assertionsResults,
        results: { expressionEvaluationResults: {}, externalServiceInvocationResults: {}, exceptions: [], exceptionsByNodeId: {} },
        counts: {},
        timestamp: "2024-01-01T00:00:00Z",
    },
});

const errorResult = (): MultipleResultsWithCountsDto => ({
    type: "Error",
    testCaseId: "tc-1",
    testCaseName: "TC1",
    error: "Something went wrong",
});

describe("getNodeAssertionResults", () => {
    it("returns undefined when testCaseResult is undefined", () => {
        expect(getNodeAssertionResults(undefined)).toBeUndefined();
    });

    it("returns undefined when status is loading", () => {
        const result: TestCaseResult = { status: "loading" };
        expect(getNodeAssertionResults(result)).toBeUndefined();
    });

    it("returns undefined when result type is Error", () => {
        const result: TestCaseResult = { status: "loaded", results: errorResult() };
        expect(getNodeAssertionResults(result)).toBeUndefined();
    });

    it("returns assertionsResults when status is loaded and type is Completed", () => {
        const assertions = { node1: [{ type: "SuccessfulAssertion" as const }] };
        const result: TestCaseResult = { status: "loaded", results: completedResult(assertions) };
        expect(getNodeAssertionResults(result)).toEqual(assertions);
    });
});

describe("testingReducer", () => {
    it("sets loading state for a test case", () => {
        const state = testingReducer(initialTestingState, { type: "SET_TEST_CASE_ASSERTION_RESULTS_LOADING", testCaseId: "tc-1" });
        expect(state.testCasesResults["tc-1"]).toEqual({ status: "loading" });
    });

    it("stores results for a test case on DISPLAY_TEST_ASSERTIONS_RESULTS", () => {
        const results = completedResult();
        const state = testingReducer(initialTestingState, { type: "DISPLAY_TEST_ASSERTIONS_RESULTS", testCaseId: "tc-1", results });
        expect(state.testCasesResults["tc-1"]).toEqual({ status: "loaded", results });
    });

    it("preserves other test case results when storing new one", () => {
        const existing = completedResult();
        const initial = { ...initialTestingState, testCasesResults: { "tc-other": { status: "loaded" as const, results: existing } } };
        const newResults = completedResult();
        const state = testingReducer(initial, { type: "DISPLAY_TEST_ASSERTIONS_RESULTS", testCaseId: "tc-1", results: newResults });
        expect(state.testCasesResults["tc-other"]).toBeDefined();
        expect(state.testCasesResults["tc-1"]).toEqual({ status: "loaded", results: newResults });
    });

    it("removes failed test case from results", () => {
        const initial = {
            ...initialTestingState,
            testCasesResults: { "tc-1": { status: "loading" as const }, "tc-2": { status: "loading" as const } },
        };
        const state = testingReducer(initial, { type: "TEST_CASE_ASSERTION_RESULTS_FAILED", testCaseId: "tc-1" });
        expect(state.testCasesResults["tc-1"]).toBeUndefined();
        expect(state.testCasesResults["tc-2"]).toBeDefined();
    });

    it("clears all results on CLEAR_TEST_ASSERTIONS_RESULTS", () => {
        const initial = { ...initialTestingState, testCasesResults: { "tc-1": { status: "loading" as const } } };
        const state = testingReducer(initial, { type: "CLEAR_TEST_ASSERTIONS_RESULTS" });
        expect(state.testCasesResults).toBeNull();
    });
});
