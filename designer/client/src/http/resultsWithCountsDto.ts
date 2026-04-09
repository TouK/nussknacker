import type { NodeId } from "../types/node";

export interface ContextIdPathPartJson {
    n: NodeId;
    t: string;
}

export interface ContextIdJson {
    nid: NodeId;
    tid: number;
    idx: number;
    path: ContextIdPathPartJson[];
}

export interface Variable {
    original?: string;
    pretty: unknown;
}

export interface ResultContextJson {
    id: string;
    cid?: ContextIdJson;
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
    cid?: ContextIdJson;
    timestamp?: string;
    name?: string;
    value?: Variable | null;
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
    nodeNamesByContextKey?: Record<string, string>;
    /** @deprecated Use exceptionsByNodeId instead */
    exceptions: ExceptionResultJson[];
    exceptionsByNodeId: Record<NodeId, ExceptionResultJson[]>;
}

export interface NodeCounts {
    errors?: number;
    all?: number;
    fragmentCounts: ProcessCounts;
}

export interface TestAssertionResultSuccess {
    type: "SuccessfulAssertion";
}

export interface TestAssertionResultError {
    type: "FailedAssertion";
    message: string;
}

export type TestAssertionResult = TestAssertionResultSuccess | TestAssertionResultError;

export type NodeAssertionResults = Record<string, TestAssertionResult[]>;

export type ProcessCounts = Record<string, NodeCounts>;

export interface ResultsWithCountsDto {
    assertionsResults: NodeAssertionResults;
    results: TestResultsDto;
    counts: ProcessCounts;
    timestamp: string; // ISO
}

type MultipleResultsWithCountsSuccessDto = { result: ResultsWithCountsDto } & {
    testCaseId: string;
    testCaseName: string;
    type: "Completed";
};

type MultipleResultsWithCountsErrorDto = { error: string } & {
    testCaseId: string;
    testCaseName: string;
    type: "Error";
};

export type MultipleResultsWithCountsDto = MultipleResultsWithCountsSuccessDto | MultipleResultsWithCountsErrorDto;
