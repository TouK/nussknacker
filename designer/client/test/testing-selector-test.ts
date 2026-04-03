import type { MultipleResultsWithCountsDto } from "../src/http/resultsWithCountsDto";
import { getFilteredTestAssertionResults } from "../src/reducers/selectors/testing";

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

const buildState = (nodeIds: string[], testCasesResults: Record<string, any>) =>
    ({
        graphReducer: {
            present: {
                scenario: {
                    scenarioGraph: {
                        nodes: nodeIds.map((id) => ({ id })),
                    },
                },
                testing: { testCasesResults },
                processCounts: {},
            },
        },
    } as any);

describe("getFilteredTestAssertionResults", () => {
    it("removes results for nodes not present in scenarioGraph", () => {
        const assertions = { node1: [{ type: "SuccessfulAssertion" as const }], node2: [{ type: "SuccessfulAssertion" as const }] };
        const state = buildState(["node2"], { "tc-1": { status: "loaded", results: completedResult(assertions) } });
        const result = getFilteredTestAssertionResults(state);
        expect(result["tc-1"]).toMatchObject({
            status: "loaded",
            results: { result: { assertionsResults: { node2: expect.anything() } } },
        });
        expect((result["tc-1"] as any).results.result.assertionsResults).not.toHaveProperty("node1");
    });

    it("keeps results for nodes present in scenarioGraph", () => {
        const assertions = { node1: [{ type: "SuccessfulAssertion" as const }] };
        const state = buildState(["node1"], { "tc-1": { status: "loaded", results: completedResult(assertions) } });
        const result = getFilteredTestAssertionResults(state);
        expect((result["tc-1"] as any).results.result.assertionsResults).toHaveProperty("node1");
    });

    it("passes through loading results unchanged", () => {
        const state = buildState([], { "tc-1": { status: "loading" } });
        const result = getFilteredTestAssertionResults(state);
        expect(result["tc-1"]).toEqual({ status: "loading" });
    });

    it("passes through Error results unchanged", () => {
        const errorResult: MultipleResultsWithCountsDto = {
            type: "Error",
            testCaseId: "tc-1",
            testCaseName: "TC1",
            error: "Something went wrong",
        };
        const state = buildState([], { "tc-1": { status: "loaded", results: errorResult } });
        const result = getFilteredTestAssertionResults(state);
        expect(result["tc-1"]).toEqual({ status: "loaded", results: errorResult });
    });

    it("filters results across multiple test cases", () => {
        const assertions = { node1: [{ type: "SuccessfulAssertion" as const }], node2: [{ type: "SuccessfulAssertion" as const }] };
        const state = buildState(["node1"], {
            "tc-1": { status: "loaded", results: completedResult(assertions) },
            "tc-2": { status: "loaded", results: completedResult(assertions) },
        });
        const result = getFilteredTestAssertionResults(state);
        for (const tcId of ["tc-1", "tc-2"]) {
            const assertionsResults = (result[tcId] as any).results.result.assertionsResults;
            expect(assertionsResults).toHaveProperty("node1");
            expect(assertionsResults).not.toHaveProperty("node2");
        }
    });

    it("returns empty assertionsResults when all nodes are deleted", () => {
        const assertions = { node1: [{ type: "SuccessfulAssertion" as const }] };
        const state = buildState([], { "tc-1": { status: "loaded", results: completedResult(assertions) } });
        const result = getFilteredTestAssertionResults(state);
        expect((result["tc-1"] as any).results.result.assertionsResults).toEqual({});
    });
});
