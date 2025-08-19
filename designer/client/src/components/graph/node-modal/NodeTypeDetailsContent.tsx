import type { SetStateAction } from "react";
import React from "react";

import { getCreatorType } from "../../../reducers/selectors/getCreator";
import type { Edge, NodeType, NodeValidationError } from "../../../types";
import { CustomNode } from "./customNode";
import { EnricherProcessor } from "./enricherProcessor";
import { Filter } from "./filter";
import FragmentInputDefinition from "./fragment-input-definition/FragmentInputDefinition";
import type { FragmentInputParameter } from "./fragment-input-definition/item";
import { FragmentInput } from "./fragmentInput";
import FragmentOutputDefinition from "./FragmentOutputDefinition";
import { JoinNode } from "./joinNode";
import { NodeDetailsFallback } from "./NodeDetailsContent/NodeDetailsFallback";
import { Sink } from "./sink";
import { Source } from "./source";
import { Split } from "./split";
import { Switch } from "./switch";
import { useNodeTypeDetailsContentLogic } from "./useNodeTypeDetailsContentLogic";
import Variable from "./Variable";
import { VariableBuilder } from "./variableBuilder";

export type NodeTypeDetailsContentProps = {
    node: NodeType;
    edges?: Edge[];
    onChange?: (node: SetStateAction<NodeType>, edges?: SetStateAction<Edge[]>) => void;
    showValidation?: boolean;
    showSwitch?: boolean;
    errors: NodeValidationError[];
};

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
