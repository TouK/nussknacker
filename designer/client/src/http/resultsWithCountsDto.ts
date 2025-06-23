import type { NodeId } from "../types";

export interface Variable {
    original?: string;
    pretty: unknown;
}

export interface ResultContextJson {
    id: string;
    timestamp: string; // ISO
    variables: Record<string, Variable>;
}

export interface ExpressionInvocationResultJson {
    contextId: ResultContextJson["id"];
    name: string;
    value: unknown;
}

export interface ExceptionResultJson {
    nodeId: NodeId;
    context: ResultContextJson;
    throwable;
}

export interface ExternalInvocationResultJson {
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
    nodeResults?: Record<NodeId, ResultContextJson[]> | null;
    nodeTransitionResults?: NodeTransitionResult[] | null;
    invocationResults: Record<NodeId, ExpressionInvocationResultJson[]>;
    externalInvocationResults: Record<NodeId, ExternalInvocationResultJson[]>;
    exceptions: ExceptionResultJson[];
}

export interface NodeCounts {
    errors?: number;
    all?: number;
    fragmentCounts: ProcessCounts;
}

export type ProcessCounts = Record<string, NodeCounts>;

export interface ResultsWithCountsDto {
    results: TestResultsDto;
    counts: ProcessCounts;
    timestamp: string; // ISO
}
