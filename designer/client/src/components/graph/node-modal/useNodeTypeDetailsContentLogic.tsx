import { get, identity, isEqual } from "lodash";
import React, { type SetStateAction, useCallback, useEffect, useMemo } from "react";

import {
    nodeValidationDynamicParametersLoaded,
    nodeValidationDynamicParametersLoading,
    validateNodeData,
} from "../../../actions/nk/nodeDetails";
import type { RootState } from "../../../reducers";
import { getUserSettings } from "../../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { Edge } from "../../../types/edge";
import type { NodeType, Parameter } from "../../../types/node";
import { ParamFieldLabel } from "./FieldLabel";
import {
    getDynamicParameterDefinitions,
    getFindAvailableBranchVariables,
    getFindAvailableVariables,
    getProcessName,
    getProcessProperties,
} from "./NodeDetailsContent/selectors";
import type { NodeTypeDetailsContentProps } from "./NodeTypeDetailsContent";
import { cleanProperties, isRequestSource } from "./requestSourceAddons";
import { setImmutable } from "./setImmutable";
import type { Paths, PathValue } from "./typeHelpers";

type ArrayElement<A extends readonly unknown[]> = A extends readonly (infer E)[] ? E : never;
export type SetProperty<O = NodeType> = <P extends Paths<O>, V extends PathValue<O, P>>(path: P, value: V, fallbackValue?: V) => void;
export type Prettify<T> = { [K in keyof T]: T[K] };

export function useValidation({ node, edges, showValidation }: Pick<NodeTypeDetailsContentProps, "node" | "edges" | "showValidation">) {
    const dispatch = useAppDispatch();
    const getBranchVariableTypes = useAppSelector(getFindAvailableBranchVariables);
    const processName = useAppSelector(getProcessName);
    const processProperties = useAppSelector(getProcessProperties);

    const settings = useAppSelector(getUserSettings);
    const autoApply = settings["node.autoApply"];

    const variableTypes = useVariableTypes({ node });

    useEffect(() => {
        if (!showValidation) return;
        let nodeData = node;
        if (isRequestSource(node)) {
            nodeData = cleanProperties(nodeData);
        }
        dispatch(
            validateNodeData(
                processName,
                {
                    //see NODES_CONNECTED/NODES_DISCONNECTED
                    outgoingEdges: edges.filter((e) => e.to != ""),
                    nodeData,
                    processProperties,
                    branchVariableTypes: getBranchVariableTypes(nodeData.id),
                    variableTypes,
                },
                () => {
                    if (autoApply) return;
                    dispatch(nodeValidationDynamicParametersLoaded(nodeData.id));
                },
            ),
        );
    }, [dispatch, edges, getBranchVariableTypes, node, processName, processProperties, showValidation, variableTypes, autoApply]);
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
                const editedParamDefinition = parameterDefinitions.find(
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
