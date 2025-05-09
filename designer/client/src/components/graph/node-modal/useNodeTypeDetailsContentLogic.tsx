import { identity, isEqual } from "lodash";
import React, { type SetStateAction, useCallback, useEffect, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";

import { validateNodeData } from "../../../actions/nk";
import type { RootState } from "../../../reducers";
import { getProcessDefinitionData } from "../../../reducers/selectors/processDefinitionData";
import type { Edge, NodeType } from "../../../types";
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

    const adjustNode = useNodeAdjust();
    const [proxyNode, setProxyNode] = useState(() => adjustNode(node));

    useEffect(() => {
        setProxyNode((currentNode) => {
            const adjustedNode = adjustNode(node);
            return isEqual(adjustedNode, currentNode) ? currentNode : adjustedNode;
        });
    }, [adjustNode, node]);

    const change = useCallback(
        (node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => {
            if (isEditMode) {
                onChange(node, edges);
            }
        },
        [isEditMode, onChange],
    );

    const setEditedNode = useCallback(
        (n: SetStateAction<NodeType>) => {
            setProxyNode((current) => {
                const nextNode = typeof n === "function" ? n(current) : n;
                if (isEqual(current, nextNode)) {
                    return current;
                }
                change(nextNode, identity);
                return nextNode;
            });
        },
        [change],
    );

    const setEditedEdges = useCallback((e: SetStateAction<Edge[]>) => change(identity, e), [change]);

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
            setEditedNode((currentNode) => setImmutable<NodeType, Paths<NodeType>>(currentNode, path, nextValue));
        },
        [setEditedNode],
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
        node: proxyNode,
    };
}
