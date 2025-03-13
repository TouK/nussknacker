import React, { createContext, PropsWithChildren, useCallback, useContext, useMemo, useReducer } from "react";
import { useSelector } from "react-redux";
import TestResultUtils from "../../../../common/TestResultUtils";
import { getProcessName, getScenarioGraph, getTestResults } from "../../../../reducers/selectors/graph";
import NodeUtils from "../../NodeUtils";
import { VariableContextType } from "./VariableContextTree";

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

type ContextType = {
    state: InputOutputState;
    dispatch: React.Dispatch<Action>;
    getAvailableContexts: (nodeIds: string[], direction?: "input" | "output") => VariableContextType[];
    prevNodes: string[];
    inputNodes: string[];
    outputNodes: string[];
};

const InputOutputContext = createContext<ContextType>(null);

export const useInputOutputContext = () => {
    const context = useContext(InputOutputContext);
    if (!context) {
        throw "used outside InputOutput context";
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
    const [state, dispatch] = useReducer(reducer, initialState);

    const scenario = useSelector(getScenarioGraph);

    const [inputNodes, outputNodes, prevNodes] = useMemo(() => {
        if (!nodeId) throw "no NodeId provided!";
        return [
            [nodeId],
            NodeUtils.getNodesConnectedToOutput(nodeId, scenario).map((n) => n.id),
            NodeUtils.getNodesConnectedToInput(nodeId, scenario).map((n) => n.id),
        ];
    }, [nodeId, scenario]);

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

    const results = useSelector(getTestResults);
    const getAvailableContexts = useCallback(
        (nodeIds: string[], direction: "input" | "output" = "input") => {
            const contexts: VariableContextType[] = [];

            nodeIds.forEach((nodeId) => {
                const testResults = TestResultUtils.resultsForNode(results, nodeId);
                testResults.nodeResults.forEach(({ id, variables }) => {
                    const foundContext = contexts.find((context) => context.id === id);
                    if (foundContext) {
                        foundContext.nodeIds.push(nodeId);
                        return;
                    }

                    const error = testResults.errors?.find(({ context }) => context.id === id);

                    contexts.push({
                        id,
                        variables,
                        disabled: isContextDisabled(id, direction),
                        nodeIds: [nodeId],
                        error: error?.throwable,
                    });
                });
            });
            return contexts;
        },
        [isContextDisabled, results],
    );

    const value = useMemo(
        () => ({
            state,
            dispatch,
            getAvailableContexts,
            prevNodes,
            inputNodes,
            outputNodes,
        }),
        [getAvailableContexts, inputNodes, outputNodes, prevNodes, state],
    );
    return <InputOutputContext.Provider value={value}>{children}</InputOutputContext.Provider>;
};
