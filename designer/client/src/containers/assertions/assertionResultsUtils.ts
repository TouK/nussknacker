import type { TestAssertionResult } from "../../http/resultsWithCountsDto";

export interface AssertionResultsSummary {
    results: TestAssertionResult[];
    hasResult: boolean;
    failedCount: number;
    passedCount: number;
    total: number;
}

export const calculateAssertionResultsSummary = (results: TestAssertionResult[]): AssertionResultsSummary => {
    const hasResult = results && results.length > 0;
    const failedCount = results.filter((r: TestAssertionResult) => r.type === "FailedAssertion").length;
    const passedCount = results.filter((r: TestAssertionResult) => r.type === "SuccessfulAssertion").length;
    const total = results.length;

    return {
        results,
        hasResult,
        failedCount,
        passedCount,
        total,
    };
};
