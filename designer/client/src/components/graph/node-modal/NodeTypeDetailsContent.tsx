import { Edge, NodeType, NodeValidationError, PropertiesType } from "../../../types";
import React, { SetStateAction, useCallback, useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import {
    getDynamicParameterDefinitions,
    getFindAvailableBranchVariables,
    getFindAvailableVariables,
    getProcessName,
    getProcessProperties,
} from "./NodeDetailsContent/selectors";
import { adjustParameters } from "./ParametersUtils";
import { generateUUIDs } from "./nodeUtils";
import { FieldLabel } from "./FieldLabel";
import { cloneDeep, isEqual, set } from "lodash";
import { nodeDetailsClosed, nodeDetailsOpened, validateNodeData } from "../../../actions/nk";
import NodeUtils from "../NodeUtils";
import { Source } from "./source";
import { Sink } from "./sink";
import FragmentInputDefinition from "./fragment-input-definition/FragmentInputDefinition";
import FragmentOutputDefinition from "./FragmentOutputDefinition";
import { Filter } from "./filter";
import { EnricherProcessor } from "./enricherProcessor";
import { FragmentInput } from "./fragmentInput";
import { JoinCustomNode } from "./joinCustomNode";
import { VariableBuilder } from "./variableBuilder";
import { Switch } from "./switch";
import { Split } from "./split";
import { Properties } from "./properties";
import { NodeDetailsFallback } from "./NodeDetailsContent/NodeDetailsFallback";
import Variable from "./Variable";
import { FragmentInputParameter } from "./fragment-input-definition/item";

type ArrayElement<A extends readonly unknown[]> = A extends readonly (infer E)[] ? E : never;

interface NodeTypeDetailsContentProps {
    node: NodeType;
    edges?: Edge[];
    onChange?: (node: SetStateAction<NodeType>, edges?: SetStateAction<Edge[]>) => void;
    showValidation?: boolean;
    showSwitch?: boolean;
    errors: NodeValidationError[];
}

export function NodeTypeDetailsContent({
    node,
    edges,
    onChange,
    errors,
    showValidation,
    showSwitch,
}: NodeTypeDetailsContentProps): JSX.Element {
    const dispatch = useDispatch();
    const isEditMode = !!onChange;

    const processDefinitionData = useSelector(getProcessDefinitionData);
    const findAvailableVariables = useSelector(getFindAvailableVariables);
    const getParameterDefinitions = useSelector(getDynamicParameterDefinitions);
    const getBranchVariableTypes = useSelector(getFindAvailableBranchVariables);
    const processName = useSelector(getProcessName);
    const processProperties = useSelector(getProcessProperties);

    const variableTypes = useMemo(() => findAvailableVariables?.(node.id), [findAvailableVariables, node.id]);

    const change = useCallback(
        (node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => {
            if (isEditMode) {
                onChange(node, edges);
            }
        },
        [isEditMode, onChange],
    );

    const setEditedNode = useCallback((n: SetStateAction<NodeType>) => change(n, edges), [edges, change]);

    const setEditedEdges = useCallback((e: SetStateAction<Edge[]>) => change(node, e), [node, change]);

    const parameterDefinitions = useMemo(() => getParameterDefinitions(node), [getParameterDefinitions, node]);

    const adjustNode = useCallback(
        (node: NodeType) => {
            const { adjustedNode } = adjustParameters(node, parameterDefinitions);
            return generateUUIDs(adjustedNode, ["fields", "parameters"]);
        },
        [parameterDefinitions],
    );

    const renderFieldLabel = useCallback(
        (paramName: string): JSX.Element => {
            return <FieldLabel parameterDefinitions={parameterDefinitions} paramName={paramName} />;
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

    const setProperty = useCallback(
        <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]): void => {
            setEditedNode((currentNode) => {
                const value = newValue == null && defaultValue != undefined ? defaultValue : newValue;
                const node = cloneDeep(currentNode);
                return set(node, property, value);
            });
        },
        [setEditedNode],
    );

    useEffect(() => {
        dispatch(nodeDetailsOpened(node.id));
        return () => {
            dispatch(nodeDetailsClosed(node.id));
        };
    }, [dispatch, node.id]);

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

    useEffect(() => {
        setEditedNode((node) => {
            const adjustedNode = adjustNode(node);
            return isEqual(adjustedNode, node) ? node : adjustedNode;
        });
    }, [adjustNode, setEditedNode]);

    switch (NodeUtils.nodeType(node)) {
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
                    findAvailableVariables={findAvailableVariables}
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
        case "CustomNode":
            return (
                <JoinCustomNode
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
        case "VariableBuilder":
            return (
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
                    findAvailableVariables={findAvailableVariables}
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
        case "Properties":
            return (
                <Properties
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node as PropertiesType}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
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
