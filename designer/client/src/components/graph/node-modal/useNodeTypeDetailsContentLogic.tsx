import { get, identity, isEqual } from "lodash";
import React, { type SetStateAction, useCallback, useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";

import { nodeValidationDataUpdating, validateNodeData } from "../../../actions/nk";
import type { RootState } from "../../../reducers";
import { getProcessDefinitionData } from "../../../reducers/selectors/processDefinitionData";
import type { Edge, NodeType, Parameter } from "../../../types";
import { ParamFieldLabel } from "./FieldLabel";
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

export function useNodeAdjust() {
    const getParameterDefinitions = useSelector(getDynamicParameterDefinitions);
    return useCallback(
        (node: NodeType) => {
            const parameterDefinitions = getParameterDefinitions(node);
            const adjustedNode = adjustParameters(node, parameterDefinitions);
            return generateUUIDs(adjustedNode, ["fields", "parameters"]);
        },
        [getParameterDefinitions],
    );
}

export function useNodeTypeDetailsContentLogic(props: Pick<NodeTypeDetailsContentProps, "onChange" | "node" | "edges" | "showValidation">) {
    const { onChange, node, edges, showValidation } = props;
    const dispatch = useDispatch();
    const isEditMode = !!onChange;

    const processDefinitionData = useSelector(getProcessDefinitionData);
    const findAvailableVariables = useSelector(getFindAvailableVariables);
    const getParameterDefinitions = useSelector(getDynamicParameterDefinitions);
    const getBranchVariableTypes = useSelector(getFindAvailableBranchVariables);
    const processName = useSelector(getProcessName);
    const processProperties = useSelector(getProcessProperties);

    const variableTypes = useSelector((s: RootState) => getFindAvailableVariables(s)?.(node.id), isEqual);

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
                function extractBasePathWithIndex(path) {
                    const match = path.match(/^(.*?\[\d+])/);
                    return match ? match[1] : path;
                }

                const basePath = extractBasePathWithIndex(path);

                const editedParam: Parameter | undefined = get(currentNode, basePath);
                const editedParamDefinition = parameterDefinitions.find(
                    (parameterDefinition) => parameterDefinition.name === editedParam?.name,
                );

                if (editedParamDefinition?.changesCanReloadParameters) {
                    dispatch(nodeValidationDataUpdating(currentNode.id));
                    setImmutable<NodeType, Paths<NodeType>>(currentNode, `${basePath}.isLoading`, true);
                    console.log(get(currentNode, `${basePath}`));
                }

                return setImmutable<NodeType, Paths<NodeType>>(currentNode, path, nextValue);
            });
        },
        [dispatch, parameterDefinitions, setEditedNode],
    );

    useEffect(() => {
        if (showValidation) {
            dispatch(
                validateNodeData(processName, {
                    //see NODES_CONNECTED/NODES_DISCONNECTED
                    outgoingEdges: edges.filter((e) => e.to != ""),
                    nodeData: node,
                    processProperties,
                    branchVariableTypes: getBranchVariableTypes(node.id),
                    variableTypes,
                }),
            );
        }
    }, [dispatch, edges, getBranchVariableTypes, node, processName, processProperties, showValidation, variableTypes]);

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
