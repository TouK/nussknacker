import type { NodeId } from "../types/node";

export interface Variable {
    original?: string;
    pretty: unknown;
}

export interface ResultContextJson {
    id: string;
    timestamp: string; // ISO
    variables: Record<string, Variable>;
}

export interface ExpressionEvaluationResultJson {
    contextId: ResultContextJson["id"];
    name: string;
    value: unknown;
}

export interface ExceptionResultJson {
    nodeId: NodeId;
    context: ResultContextJson;
    throwable;
}

export interface ExternalServiceInvocationResultJson {
    contextId: ResultContextJson["id"];
}

export type NodeTransitionResult = {
    sourceNodeId: NodeId;
    destinationNodeId: NodeId | null;
    results: ResultContextJson[];
    totalCount: number | null;
    currentThroughput: number | null;
};

export interface TestResultsDto {
    /** @deprecated Use nodeTransitionResults instead */
    nodeResults?: Record<NodeId, ResultContextJson[]> | null;
    nodeTransitionResults?: NodeTransitionResult[] | null;
    expressionEvaluationResults: Record<NodeId, ExpressionEvaluationResultJson[]>;
    externalServiceInvocationResults: Record<NodeId, ExternalServiceInvocationResultJson[]>;
    /** @deprecated Use exceptionsByNodeId instead */
    exceptions: ExceptionResultJson[];
    exceptionsByNodeId: Record<NodeId, ExceptionResultJson[]>;
}

export interface NodeCounts {
    errors?: number;
    all?: number;
    fragmentCounts: ProcessCounts;
}

interface TestAssertionResultSuccess {
    type: "SuccessfulAssertion";
}

interface TestAssertionResultError {
    type: "FailedAssertion";
    message: string;
}

type TestAssertionResult = TestAssertionResultSuccess | TestAssertionResultError;

export type TestAssertionResults = Record<string, TestAssertionResult[]>;

export type ProcessCounts = Record<string, NodeCounts>;

export interface ResultsWithCountsDto {
    assertionsResults: TestAssertionResults;
    results: TestResultsDto;
    counts: ProcessCounts;
    timestamp: string; // ISO
}
