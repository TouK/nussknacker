import { cloneDeep, isEqual, set } from "lodash";
import React, { SetStateAction, useCallback, useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";
import { nodeDetailsClosed, nodeDetailsOpened, validateNodeData } from "../../../actions/nk";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { Edge, NodeType, NodeValidationError, PropertiesType } from "../../../types";
import { CustomNode } from "./customNode";
import { EnricherProcessor } from "./enricherProcessor";
import { ParamFieldLabel } from "./FieldLabel";
import { Filter } from "./filter";
import FragmentInputDefinition from "./fragment-input-definition/FragmentInputDefinition";
import { FragmentInputParameter } from "./fragment-input-definition/item";
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
import { Sink } from "./sink";
import { Source } from "./source";
import { Split } from "./split";
import { Switch } from "./switch";
import Variable from "./Variable";
import { VariableBuilder } from "./variableBuilder";
import { isValidationResultPresent } from "../../../reducers/selectors/graph";
import { NodeCommonDetailsDefinition } from "./NodeCommonDetailsDefinition";
import { Box, CircularProgress } from "@mui/material";
import { RootState } from "../../../reducers";
import { getNode } from "./node/selectors";

type ArrayElement<A extends readonly unknown[]> = A extends readonly (infer E)[] ? E : never;

export type NodeTypeDetailsContentProps = {
    node: NodeType;
    edges?: Edge[];
    onChange?: (node: SetStateAction<NodeType>, edges?: SetStateAction<Edge[]>) => void;
    showValidation?: boolean;
    showSwitch?: boolean;
    errors: NodeValidationError[];
};

export function useNodeTypeDetailsContentLogic(props: Pick<NodeTypeDetailsContentProps, "onChange" | "node" | "edges" | "showValidation">) {
    const { onChange, node: originalNode, edges, showValidation } = props;

    const node = useSelector((state: RootState) => {
        return getNode(state, originalNode.id);
    });

    const validationResultPresent = useSelector(isValidationResultPresent);
    const dispatch = useDispatch();
    const isEditMode = !!onChange && validationResultPresent;

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

    return {
        validationResultPresent,
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
    };
}

export function NodeTypeDetailsContent({ errors, showSwitch, ...props }: NodeTypeDetailsContentProps): JSX.Element {
    const {
        validationResultPresent,
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

    if (!validationResultPresent) {
        return (
            <NodeCommonDetailsDefinition
                node={node}
                setProperty={setProperty}
                readOnly={!isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                errors={errors}
            >
                <Box display={"flex"} justifyContent={"center"} height={"20%"} alignItems={"center"}>
                    <CircularProgress />
                </Box>
            </NodeCommonDetailsDefinition>
        );
    }

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
