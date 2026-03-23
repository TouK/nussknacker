import type { PropsWithChildren } from "react";
import React, { createContext, memo, useCallback, useContext, useMemo, useReducer } from "react";

import TestResultUtils from "../../../../common/TestResultUtils";
import type { ExternalServiceInvocationResultJson, NodeTransitionResult, ResultContextJson } from "../../../../http/resultsWithCountsDto";
import { getScenarioGraph } from "../../../../reducers/selectors/graph";
import { getTestResults } from "../../../../reducers/selectors/testing";
import { useAppSelector } from "../../../../store/storeHelpers";
import NodeUtils from "../../NodeUtils";
import type { VariableContextType } from "./VariableContextTree";

export type InputOutputState = {
    inputDataSetId?: string | null;
    outputDataSetId?: string | null;
    inputVariables?: string[];
};

const initialState: InputOutputState = {};

type Action =
    | {
          type: "selectInputContext";
          context: VariableContextType;
      }
    | {
          type: "selectOutputContext";
          context: VariableContextType;
      };

export type ResultsWithId = NodeTransitionResult & { id: string };
type ContextType = {
    state: InputOutputState;
    dispatch: React.Dispatch<Action>;
    getAvailableContexts: (direction?: "input" | "output", filter?: string[]) => [VariableContextType[], number];
    inputNodesIds: ResultsWithId[];
    outputNodesIds: ResultsWithId[];
};

const InputOutputContext = createContext<ContextType>(null);

export const useInputOutputContext = () => {
    const context = useContext(InputOutputContext);
    if (!context) {
        console.warn("used outside InputOutput context");
        return null;
    }
    return context;
};

const reducer = (state: InputOutputState, action: Action) => {
    switch (action?.type) {
        case "selectInputContext":
            return {
                ...state,
                inputDataSetId: action.context && state.inputDataSetId !== action.context.id ? action.context.id : null,
                inputVariables: Object.keys(action.context?.variables || {}),
            };
        case "selectOutputContext":
            return {
                ...state,
                outputDataSetId: action.context && state.outputDataSetId !== action.context.id ? action.context.id : null,
            };
        default:
            return state;
    }
};

const createOutputResultsFromExternalInvocations = (
    externalInvocationResults: ExternalServiceInvocationResultJson[],
    inputResultsByContextId: Map<string, ResultContextJson>,
): ResultContextJson[] => {
    const byContextId = new Map<string, ExternalServiceInvocationResultJson[]>();
    externalInvocationResults.forEach((result) => {
        const existing = byContextId.get(result.contextId) || [];
        existing.push(result);
        byContextId.set(result.contextId, existing);
    });

    return [...byContextId.entries()].map(([contextId, invocations]) => {
        const [firstInvocation] = invocations;
        const inputResult = inputResultsByContextId.get(contextId);
        const variables = invocations.reduce<ResultContextJson["variables"]>((acc, invocation, index) => {
            const fallbackName = `Service invocation ${index + 1}`;
            acc[invocation.name || fallbackName] = invocation.value || { pretty: null };
            return acc;
        }, {});

        return {
            id: contextId,
            cid: firstInvocation?.cid || inputResult?.cid,
            timestamp: firstInvocation?.timestamp || inputResult?.timestamp || new Date(0).toISOString(),
            variables,
        };
    });
};

export const InputOutputContextProvider = memo(function InputOutputContextProvider({
    nodeId,
    children,
}: PropsWithChildren<{
    nodeId: string[];
}>) {
    const scenario = useAppSelector(getScenarioGraph);
    const testResults = useAppSelector(getTestResults);

    const [state, dispatch] = useReducer(reducer, initialState);

    const nodeTransitionResults = useMemo(
        () =>
            nodeId.flatMap((nodeId) => {
                return testResults?.nodeTransitionResults?.filter((r) => r.destinationNodeId === nodeId || r.sourceNodeId === nodeId) || [];
            }),
        [nodeId, testResults?.nodeTransitionResults],
    );

    const inputs = useMemo(() => {
        const transitionResults = nodeId.flatMap((nodeId) => {
            return nodeTransitionResults.filter((r) => r.destinationNodeId === nodeId);
        });
        const connectedNodes = nodeId.flatMap((nodeId) => NodeUtils.getNodesConnectedToInput(nodeId, scenario).map((n) => n.id));

        return connectedNodes.map((id) => ({
            id,
            ...transitionResults.find((r) => r.sourceNodeId === id),
        }));
    }, [nodeId, nodeTransitionResults, scenario]);

    const outputs = useMemo(() => {
        const connectedNodes: (string | null)[] = nodeId.flatMap((nodeId) =>
            NodeUtils.getNodesConnectedToOutput(nodeId, scenario).map((n) => n.id),
        );
        const transitionResultsFromGraph = nodeId.flatMap((nodeId) => nodeTransitionResults.filter((r) => r.sourceNodeId === nodeId));
        const transitionResultsFromSinkInvocations: NodeTransitionResult[] = nodeId.flatMap((currentNodeId) => {
            if (transitionResultsFromGraph.some((result) => result.sourceNodeId === currentNodeId)) {
                return [];
            }
            const externalInvocationResults = testResults?.externalServiceInvocationResults?.[currentNodeId] || [];
            if (!externalInvocationResults.length) {
                return [];
            }

            const inputResultsByContextId = new Map(
                nodeTransitionResults
                    .filter((result) => result.destinationNodeId === currentNodeId)
                    .flatMap((result) => result.results)
                    .map((result) => [result.id, result]),
            );

            const outputResults = createOutputResultsFromExternalInvocations(externalInvocationResults, inputResultsByContextId);
            return [
                {
                    sourceNodeId: currentNodeId,
                    destinationNodeId: null,
                    results: outputResults,
                    totalCount: outputResults.length,
                    currentThroughput: null,
                },
            ];
        });
        const transitionResults = [...transitionResultsFromGraph, ...transitionResultsFromSinkInvocations];

        if (transitionResults?.length) {
            connectedNodes.push(null); // connection to "void"
        }

        return connectedNodes.map((id) => ({
            id,
            ...transitionResults?.find((r) => r.destinationNodeId == id),
        }));
    }, [nodeId, nodeTransitionResults, scenario, testResults?.externalServiceInvocationResults]);

    const isContextDisabled = useCallback(
        (id: string, direction: "input" | "output" = "input") => {
            switch (direction) {
                case "output":
                    return state.inputDataSetId && !id.startsWith(`${state.inputDataSetId}`);
                default:
                    return false;
            }
        },
        [state.inputDataSetId],
    );

    const getError = useCallback(
        (destinationNodeId: string, contextId: string) =>
            TestResultUtils.errorsForNode(testResults, destinationNodeId).find(({ context }) => context.id === contextId),
        [testResults],
    );

    const getAvailableContexts = useCallback(
        (direction: "input" | "output" = "input", filter: string[] = []): [VariableContextType[], number] => {
            const transitionResults = direction === "input" ? inputs : outputs;
            const contexts: VariableContextType[] = [];
            transitionResults
                .filter(({ sourceNodeId, destinationNodeId }) => {
                    if (!filter.length) return true;
                    const subject = direction === "input" ? sourceNodeId : destinationNodeId;
                    return filter.includes(subject);
                })
                .forEach(({ id: contextNodeId, destinationNodeId, results }) => {
                    results?.forEach(({ id, variables, timestamp }) => {
                        const foundContext = contexts.find((context) => context.id === id && context.timestamp === timestamp);
                        if (foundContext) {
                            foundContext.nodeIds.push(contextNodeId);
                            return;
                        }

                        const error = direction === "input" && getError(destinationNodeId, id);

                        contexts.push({
                            id,
                            variables,
                            disabled: isContextDisabled(id, direction),
                            nodeIds: [contextNodeId],
                            error: error?.throwable,
                            timestamp,
                        });
                    });
                });
            const count = transitionResults.reduce((sum, { totalCount = 0 }) => sum + totalCount, 0);
            return [contexts, count];
        },
        [inputs, outputs, getError, isContextDisabled],
    );

    const value = useMemo<ContextType>(
        () => ({
            state,
            dispatch,
            getAvailableContexts,
            inputNodesIds: inputs,
            outputNodesIds: outputs,
        }),
        [getAvailableContexts, inputs, outputs, state],
    );
    return <InputOutputContext.Provider value={value}>{children}</InputOutputContext.Provider>;
});
