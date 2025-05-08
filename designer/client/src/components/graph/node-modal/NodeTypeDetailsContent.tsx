import { isEqual } from "lodash";
import type { SetStateAction } from "react";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";

import { validateNodeData } from "../../../actions/nk";
import type { RootState } from "../../../reducers";
import { getCreatorType } from "../../../reducers/selectors/getCreator";
import { getProcessDefinitionData } from "../../../reducers/selectors/processDefinitionData";
import type { Edge, NodeType, NodeValidationError } from "../../../types";
import { CustomNode } from "./customNode";
import { EnricherProcessor } from "./enricherProcessor";
import { ParamFieldLabel } from "./FieldLabel";
import { Filter } from "./filter";
import FragmentInputDefinition from "./fragment-input-definition/FragmentInputDefinition";
import type { FragmentInputParameter } from "./fragment-input-definition/item";
import { FragmentInput } from "./fragmentInput";
import FragmentOutputDefinition from "./FragmentOutputDefinition";
import { JoinNode } from "./joinNode";
import { NodeDetailsFallback } from "./NodeDetailsContent/NodeDetailsFallback";
import {
    getDynamicParameterDefinitions,
    getFindAvailableBranchVariables,
    getFindAvailableVariables,
    getProcessName,
    getProcessProperties,
} from "./NodeDetailsContent/selectors";
import { generateUUIDs } from "./nodeUtils";
import { adjustParameters } from "./ParametersUtils";
import { setImmutable } from "./setImmutable";
import { Sink } from "./sink";
import { Source } from "./source";
import { Split } from "./split";
import { StickyNote } from "./stickyNote";
import { Switch } from "./switch";
import type { Paths, PathValue } from "./typeHelpers";
import Variable from "./Variable";
import { VariableBuilder } from "./variableBuilder";

type ArrayElement<A extends readonly unknown[]> = A extends readonly (infer E)[] ? E : never;
export type SetProperty<O = NodeType> = <P extends Paths<O>, V extends PathValue<O, P>>(path: P, value: V, fallbackValue?: V) => void;

export type NodeTypeDetailsContentProps = {
    node: NodeType;
    edges?: Edge[];
    onChange?: (node: SetStateAction<NodeType>, edges?: SetStateAction<Edge[]>) => void;
    showValidation?: boolean;
    showSwitch?: boolean;
    errors: NodeValidationError[];
};

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
                change(nextNode, edges);
                return nextNode;
            });
        },
        [edges, change],
    );

    const setEditedEdges = useCallback((e: SetStateAction<Edge[]>) => change(node, e), [node, change]);

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

export function NodeTypeDetailsContent({ errors, showSwitch, ...props }: NodeTypeDetailsContentProps): JSX.Element {
    const {
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
        edges,
        showValidation,
    } = useNodeTypeDetailsContentLogic(props);

    switch (node.type) {
        case "Source":
            return (
                <Source
                    errors={errors}
                    findAvailableVariables={findAvailableVariables}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            );
        case "Sink":
            return (
                <Sink
                    errors={errors}
                    findAvailableVariables={findAvailableVariables}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            );
        case "FragmentInputDefinition":
            return (
                <FragmentInputDefinition
                    addElement={addElement}
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node as NodeType<FragmentInputParameter>}
                    removeElement={removeElement}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showValidation={showValidation}
                    variableTypes={variableTypes}
                />
            );
        case "FragmentOutputDefinition":
            return (
                <FragmentOutputDefinition
                    addElement={addElement}
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    removeElement={removeElement}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showValidation={showValidation}
                    variableTypes={variableTypes}
                />
            );
        case "Filter":
            return (
                <Filter
                    edges={edges}
                    errors={errors}
                    variableTypes={variableTypes}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    renderFieldLabel={renderFieldLabel}
                    setEditedEdges={setEditedEdges}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            );
        case "Enricher":
        case "Processor":
            return (
                <EnricherProcessor
                    errors={errors}
                    findAvailableVariables={findAvailableVariables}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            );
        case "FragmentInput":
            return (
                <FragmentInput
                    errors={errors}
                    findAvailableVariables={findAvailableVariables}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    processDefinitionData={processDefinitionData}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            );
        case "Join":
            return (
                <JoinNode
                    errors={errors}
                    findAvailableVariables={findAvailableVariables}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    processDefinitionData={processDefinitionData}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            );
        case "CustomNode":
            return (
                <CustomNode
                    errors={errors}
                    findAvailableVariables={findAvailableVariables}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    processDefinitionData={processDefinitionData}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            );
        case "VariableBuilder": {
            return getCreatorType(node) ? null : (
                <VariableBuilder
                    addElement={addElement}
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    removeElement={removeElement}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showValidation={showValidation}
                    variableTypes={variableTypes}
                />
            );
        }
        case "Variable":
            return (
                <Variable
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showValidation={showValidation}
                    variableTypes={variableTypes}
                />
            );
        case "Switch":
            return (
                <Switch
                    edges={edges}
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    processDefinitionData={processDefinitionData}
                    renderFieldLabel={renderFieldLabel}
                    setEditedEdges={setEditedEdges}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                    variableTypes={variableTypes}
                />
            );
        case "Split":
            return (
                <Split
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showValidation={showValidation}
                />
            );
        case "StickyNoteNode":
            return (
                <StickyNote
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showValidation={showValidation}
                />
            );
        default:
            return (
                <NodeDetailsFallback
                    errors={errors}
                    node={node}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                />
            );
    }
}
