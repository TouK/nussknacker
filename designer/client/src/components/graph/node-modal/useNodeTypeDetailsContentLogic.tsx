import { get, identity, isEqual, partition } from "lodash";
import React, { type SetStateAction, useCallback, useEffect, useMemo } from "react";

import {
    nodeValidationDynamicParametersLoaded,
    nodeValidationDynamicParametersLoading,
    validateNodeData,
} from "../../../actions/nk/nodeDetails";
import type { RootState } from "../../../reducers";
import { getTestCase, getTestCaseNodeValidationData } from "../../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { Edge } from "../../../types/edge";
import type { NodeType, Parameter } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { ParamFieldLabel } from "./FieldLabel";
import { getNodeErrors } from "./node/selectors";
import {
    getDynamicParameterDefinitions,
    getFindAvailableBranchVariables,
    getFindAvailableVariables,
    getNodeErrors as getNodeCurrentErrors,
    getProcessName,
    getValidationTestCasesErrors,
} from "./NodeDetailsContent/selectors";
import type { NodeTypeDetailsContentProps } from "./NodeTypeDetailsContent";
import { setImmutable } from "./setImmutable";
import type { Paths, PathValue } from "./typeHelpers";

type ArrayElement<A extends readonly unknown[]> = A extends readonly (infer E)[] ? E : never;
export type SetProperty<O = NodeType> = <P extends Paths<O>, V extends PathValue<O, P>>(path: P, value: V, fallbackValue?: V) => void;
export type Prettify<T> = T extends any ? { [K in keyof T]: T[K] } : never;

export function useValidation({ node, edges, showValidation }: Pick<NodeTypeDetailsContentProps, "node" | "edges" | "showValidation">) {
    const dispatch = useAppDispatch();
    const getBranchVariableTypes = useAppSelector(getFindAvailableBranchVariables);
    const processName = useAppSelector(getProcessName);
    const testCaseValidationData = useAppSelector((state) => getTestCaseNodeValidationData(state, node.id));

    const variableTypes = useVariableTypes({ node });

    useEffect(() => {
        if (!showValidation) return;
        dispatch(
            validateNodeData(
                {
                    //see NODES_CONNECTED/NODES_DISCONNECTED
                    outgoingEdges: edges.filter((e) => e.to != ""),
                    nodeData: node,
                    branchVariableTypes: getBranchVariableTypes(node.id),
                    variableTypes,
                    testCases: testCaseValidationData,
                },
                () => {
                    dispatch(nodeValidationDynamicParametersLoaded(node.id));
                },
            ),
        );
    }, [dispatch, edges, getBranchVariableTypes, node, processName, showValidation, testCaseValidationData, variableTypes]);
}

export function useVariableTypes({ node }: Pick<NodeTypeDetailsContentProps, "node">) {
    return useAppSelector((s: RootState) => getFindAvailableVariables(s)?.(node.id), isEqual);
}

export function useParameterDefinitions({ node }: Pick<NodeTypeDetailsContentProps, "node">) {
    const getParameterDefinitions = useAppSelector(getDynamicParameterDefinitions);
    return useMemo(() => getParameterDefinitions(node), [getParameterDefinitions, node]);
}

export function useSetProperty({ onChange, node }: Pick<NodeTypeDetailsContentProps, "onChange" | "node">) {
    const dispatch = useAppDispatch();
    const setEditedNode = useSetEditedNode({ onChange });
    const parameterDefinitions = useParameterDefinitions({ node });

    return useCallback<SetProperty>(
        <P extends Paths<NodeType>, V extends PathValue<NodeType, P>>(path: P, value: V, fallbackValue?: V): void => {
            const nextValue = value === null && fallbackValue !== undefined ? fallbackValue : value;
            setEditedNode((currentNode) => {
                function extractBasePathWithIndex(path: string) {
                    const match = path.match(/^(.*?\[\d+])/);
                    return match ? match[1] : path;
                }

                const basePath = extractBasePathWithIndex(path);

                const editedParam: Parameter | undefined = get(currentNode, basePath);
                const editedParamDefinition = parameterDefinitions?.find(
                    (parameterDefinition) => parameterDefinition.name === editedParam?.name,
                );

                const nextNode = setImmutable<NodeType, Paths<NodeType>>(currentNode, path, nextValue);
                const detectChanges = !isEqual(nextNode, currentNode);

                if (editedParamDefinition?.changesCanReloadParameters && detectChanges) {
                    dispatch(nodeValidationDynamicParametersLoading(currentNode.id, [editedParamDefinition.name]));
                }

                return nextNode;
            });
        },
        [dispatch, parameterDefinitions, setEditedNode],
    );
}

export function useSetEditedNode({ onChange }: Pick<NodeTypeDetailsContentProps, "onChange">) {
    return useCallback((n: SetStateAction<NodeType>) => onChange?.(n, identity), [onChange]);
}

export function useSetEditedEdges({ onChange }: Pick<NodeTypeDetailsContentProps, "onChange">) {
    return useCallback((e: SetStateAction<Edge[]>) => onChange?.(identity, e), [onChange]);
}

export function useAddElement({ onChange }: Pick<NodeTypeDetailsContentProps, "onChange">) {
    const setEditedNode = useSetEditedNode({ onChange });
    return useCallback(
        <K extends keyof NodeType>(property: K, element: ArrayElement<NodeType[K]>): void => {
            setEditedNode((currentNode) => ({
                ...currentNode,
                [property]: [...currentNode[property], element],
            }));
        },
        [setEditedNode],
    );
}

export function useRemoveElement({ onChange }: Pick<NodeTypeDetailsContentProps, "onChange">) {
    const setEditedNode = useSetEditedNode({ onChange });
    return useCallback(
        (property: keyof NodeType, uuid: string): void => {
            setEditedNode((currentNode) => ({
                ...currentNode,
                [property]: currentNode[property]?.filter((item) => item.uuid !== uuid) || [],
            }));
        },
        [setEditedNode],
    );
}

export function useRenderFieldLabel({ node }: Pick<NodeTypeDetailsContentProps, "node">) {
    const parameterDefinitions = useParameterDefinitions({ node });

    return useCallback(
        (paramName: string) => <ParamFieldLabel parameterDefinitions={parameterDefinitions} paramName={paramName} />,
        [parameterDefinitions],
    );
}

export function useIsEditMode({ onChange }: Pick<NodeTypeDetailsContentProps, "onChange">) {
    return !!onChange;
}

export function useGetNodeErrors(node: NodeType) {
    const nodeErrors = useAppSelector((state: RootState) => {
        return getNodeErrors(state, node.id);
    }, isEqual);

    const currentErrors = useAppSelector((state: RootState) => getNodeCurrentErrors(state, { node, nodeErrors }));

    const [errors, diagramStructureErrors] = useMemo(() => partition(currentErrors, (error) => !!error.fieldName), [currentErrors]);

    return [errors, diagramStructureErrors];
}

export function useGetNodeTestCasesErrors(node: NodeType): {
    enricherMockErrors: NodeValidationError[];
    assertionsErrors: NodeValidationError[];
} {
    const testCase = useAppSelector(getTestCase);
    const currentTestCasesErrors = useAppSelector((state: RootState) =>
        getValidationTestCasesErrors(state, { nodeId: node.id, testCaseId: testCase.name }),
    );

    return currentTestCasesErrors ?? { enricherMockErrors: [], assertionsErrors: [] };
}
