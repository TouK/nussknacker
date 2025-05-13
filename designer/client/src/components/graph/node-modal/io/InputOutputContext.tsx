import type { PropsWithChildren } from "react";
import React, { createContext, useCallback, useContext, useMemo, useReducer } from "react";
import { useSelector } from "react-redux";

import type { TransitionResults } from "../../../../common/TestResultUtils";
import TestResultUtils from "../../../../common/TestResultUtils";
import { getScenarioGraph, getTestResults } from "../../../../reducers/selectors/graph";
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

type Created = TransitionResults & { id: string };
type ContextType = {
    state: InputOutputState;
    dispatch: React.Dispatch<Action>;
    getAvailableContexts: (direction?: "input" | "output") => VariableContextType[];
    inputNodesIds: Created[];
    outputNodesIds: Created[];
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

export const InputOutputContextProvider = ({
    nodeId,
    children,
}: PropsWithChildren<{
    nodeId: string;
}>) => {
    const scenario = useSelector(getScenarioGraph);
    const testResults = useSelector(getTestResults);

    const [state, dispatch] = useReducer(reducer, initialState);

    const nodeTransitionResults = useMemo(
        () => testResults?.nodeTransitionResults?.filter((r) => r.destinationNodeId === nodeId || r.sourceNodeId === nodeId),
        [nodeId, testResults?.nodeTransitionResults],
    );
    const inputs = useMemo(() => {
        return NodeUtils.getNodesConnectedToInput(nodeId, scenario).map(({ id }) => {
            const transitionResults = nodeTransitionResults?.find((r) => r.destinationNodeId === nodeId && r.sourceNodeId === id);
            return {
                id,
                ...transitionResults,
            };
        });
    }, [nodeId, nodeTransitionResults, scenario]);
    const outputs = useMemo(() => {
        return NodeUtils.getNodesConnectedToOutput(nodeId, scenario).map(({ id }) => {
            const transitionResults = nodeTransitionResults?.find((r) => r.sourceNodeId === nodeId && r.destinationNodeId === id);
            return {
                id,
                ...transitionResults,
            };
        });
    }, [nodeId, nodeTransitionResults, scenario]);

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
            TestResultUtils.resultsForNode(testResults, destinationNodeId)?.errors?.find(({ context }) => context.id === contextId),
        [testResults],
    );

    const getAvailableContexts = useCallback(
        (direction: "input" | "output" = "input") => {
            const transitionResults = direction === "input" ? inputs : outputs;
            const contexts: VariableContextType[] = [];
            transitionResults.forEach(({ id: contextNodeId, destinationNodeId, results }) => {
                results?.forEach(({ id, variables }) => {
                    const foundContext = contexts.find((context) => context.id === id);
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
                    });
                });
            });
            return contexts;
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
};
