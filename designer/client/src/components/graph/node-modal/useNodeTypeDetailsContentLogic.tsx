import { get, identity, isEqual } from "lodash";
import React, { type SetStateAction, useCallback, useEffect, useLayoutEffect, useMemo, useRef } from "react";

import { nodeValidationDynamicParametersLoaded, nodeValidationDynamicParametersLoading, validateNodeData } from "../../../actions/nk";
import { useUserSettings } from "../../../common/userSettings";
import type { RootState } from "../../../reducers";
import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { Edge, NodeType, Parameter } from "../../../types";
import { ParamFieldLabel } from "./FieldLabel";
import type { NodeState } from "./node/useNodeState";
import {
    getDynamicParameterDefinitions,
    getFindAvailableBranchVariables,
    getFindAvailableVariables,
    getProcessName,
    getProcessProperties,
} from "./NodeDetailsContent/selectors";
import type { NodeTypeDetailsContentProps } from "./NodeTypeDetailsContent";
import { generateUUIDs } from "./nodeUtils";
import { adjustParameters } from "./ParametersUtils";
import { setImmutable } from "./setImmutable";
import type { Paths, PathValue } from "./typeHelpers";

type ArrayElement<A extends readonly unknown[]> = A extends readonly (infer E)[] ? E : never;
export type SetProperty<O = NodeType> = <P extends Paths<O>, V extends PathValue<O, P>>(path: P, value: V, fallbackValue?: V) => void;

function wrapSetState<T>(action: SetStateAction<T>, transform: (value: T) => T = identity): SetStateAction<T> {
    function isPlainValue<T>(action: SetStateAction<T>): action is T {
        return typeof action !== "function";
    }

    return (prev) => {
        return isPlainValue(action) ? transform(action) : action(transform(prev));
    };
}

export function useNodeAdjust(
    node: NodeType,
    onChange: NodeState["onChange"],
): [adjustedNode: typeof node, adjustedOnChange: typeof onChange] {
    const getParameterDefinitions = useAppSelector(getDynamicParameterDefinitions);

    const adjustNode = useCallback(
        (node: NodeType) => {
            const parameterDefinitions = getParameterDefinitions(node);
            const adjustedNode = adjustParameters(node, parameterDefinitions);
            return generateUUIDs(adjustedNode, ["fields", "parameters"]);
        },
        [getParameterDefinitions],
    );

    const adjustFn = useRef(adjustNode);
    useLayoutEffect(() => {
        adjustFn.current = adjustNode;
    }, [adjustNode]);

    const adjustedNode = useMemo<typeof node>(() => adjustNode(node), [adjustNode, node]);

    const adjustedOnChange = useCallback<typeof onChange>(
        (setNodeAction, setEdgesAction) => onChange(wrapSetState(setNodeAction, adjustFn?.current), setEdgesAction),
        [onChange],
    );

    return [adjustedNode, adjustedOnChange];
}

export function useNodeTypeDetailsContentLogic(props: Pick<NodeTypeDetailsContentProps, "onChange" | "node" | "edges" | "showValidation">) {
    const { onChange, node, edges, showValidation } = props;
    const dispatch = useAppDispatch();
    const isEditMode = !!onChange;

    const processDefinitionData = useAppSelector(getProcessDefinitionData);
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const getParameterDefinitions = useAppSelector(getDynamicParameterDefinitions);
    const getBranchVariableTypes = useAppSelector(getFindAvailableBranchVariables);
    const processName = useAppSelector(getProcessName);
    const processProperties = useAppSelector(getProcessProperties);
    const [settings] = useUserSettings();
    const autoApply = settings["node.autoApply"];

    const variableTypes = useAppSelector((s: RootState) => getFindAvailableVariables(s)?.(node.id), isEqual);

    const setEditedNode = useCallback((n: SetStateAction<NodeType>) => onChange?.(n, identity), [onChange]);
    const setEditedEdges = useCallback((e: SetStateAction<Edge[]>) => onChange?.(identity, e), [onChange]);

    const parameterDefinitions = useMemo(() => getParameterDefinitions(node), [getParameterDefinitions, node]);

    const renderFieldLabel = useCallback(
        (paramName: string): JSX.Element => {
            return <ParamFieldLabel parameterDefinitions={parameterDefinitions} paramName={paramName} />;
        },
        [parameterDefinitions],
    );

    const removeElement = useCallback(
        (property: keyof NodeType, uuid: string): void => {
            setEditedNode((currentNode) => ({
                ...currentNode,
                [property]: currentNode[property]?.filter((item) => item.uuid !== uuid) || [],
            }));
        },
        [setEditedNode],
    );

    const addElement = useCallback(
        <K extends keyof NodeType>(property: K, element: ArrayElement<NodeType[K]>): void => {
            setEditedNode((currentNode) => ({
                ...currentNode,
                [property]: [...currentNode[property], element],
            }));
        },
        [setEditedNode],
    );

    const setProperty = useCallback<SetProperty>(
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

    useEffect(() => {
        if (showValidation) {
            dispatch(
                validateNodeData(
                    processName,
                    {
                        //see NODES_CONNECTED/NODES_DISCONNECTED
                        outgoingEdges: edges.filter((e) => e.to != ""),
                        nodeData: node,
                        processProperties,
                        branchVariableTypes: getBranchVariableTypes(node.id),
                        variableTypes,
                    },
                    () => {
                        if (!autoApply) {
                            dispatch(nodeValidationDynamicParametersLoaded(node.id));
                        }
                    },
                ),
            );
        }
    }, [dispatch, edges, getBranchVariableTypes, node, processName, processProperties, showValidation, variableTypes, autoApply]);

    return {
        ...props,
        isEditMode,
        processDefinitionData,
        findAvailableVariables,
        variableTypes,
        setEditedEdges,
        parameterDefinitions,
        renderFieldLabel,
        removeElement,
        addElement,
        setProperty,
        node,
    };
}
