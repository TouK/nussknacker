import type { MultipleResultsWithCountsDto } from "../../../../../http/resultsWithCountsDto";
import type { TestCaseResult } from "../../../../../reducers/graph/testing";
import { getAssertionResultsSummary } from "./getAssertionResultsSummary";

const makeCompleted = (assertionsResults = {}): MultipleResultsWithCountsDto => ({
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

describe("getAssertionResultsSummary", () => {
    it("returns null status when testCaseResult is undefined", () => {
        expect(getAssertionResultsSummary(undefined)).toEqual({ status: null });
    });

    it("returns loading status when test case is loading", () => {
        const result: TestCaseResult = { status: "loading" };
        expect(getAssertionResultsSummary(result)).toEqual({ status: "loading" });
    });

    it("returns null status when result type is Error", () => {
        const result: TestCaseResult = {
            status: "loaded",
            results: { type: "Error", testCaseId: "tc-1", testCaseName: "TC1", error: "fail" },
        };
        expect(getAssertionResultsSummary(result)).toEqual({ status: null });
    });

    it("returns noAssertions status when there are no assertions", () => {
        const result: TestCaseResult = { status: "loaded", results: makeCompleted({}) };
        expect(getAssertionResultsSummary(result)).toEqual({ status: "noAssertions" });
    });

    it("returns success status when all assertions pass", () => {
        const assertions = { node1: [{ type: "SuccessfulAssertion" as const }] };
        const result: TestCaseResult = { status: "loaded", results: makeCompleted(assertions) };
        expect(getAssertionResultsSummary(result)).toEqual({ status: "success" });
    });

    it("returns error status when any assertion fails", () => {
        const assertions = {
            node1: [{ type: "SuccessfulAssertion" as const }],
            node2: [{ type: "FailedAssertion" as const, message: "expected X got Y" }],
        };
        const result: TestCaseResult = { status: "loaded", results: makeCompleted(assertions) };
        expect(getAssertionResultsSummary(result)).toEqual({ status: "error" });
    });
});
