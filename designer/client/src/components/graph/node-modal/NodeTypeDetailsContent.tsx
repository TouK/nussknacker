import type { SetStateAction } from "react";
import React from "react";

import { getCreatorType } from "../../../reducers/selectors/getCreator";
import type { Edge } from "../../../types/edge";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { CustomNode } from "./customNode";
import { FieldLabelProvider } from "./editors/RenderFieldLabel";
import { EnricherProcessor } from "./enricherProcessor";
import { Filter } from "./filter";
import FragmentInputDefinition from "./fragment-input-definition/FragmentInputDefinition";
import type { FragmentInputParameter } from "./fragment-input-definition/item/types";
import { FragmentInput } from "./fragmentInput";
import FragmentOutputDefinition from "./FragmentOutputDefinition";
import { JoinNode } from "./joinNode";
import { NodeDetailsFallback } from "./NodeDetailsContent/NodeDetailsFallback";
import { Sink } from "./sink";
import { Source } from "./source";
import { Split } from "./split";
import { Switch } from "./switch";
import type { Prettify } from "./useNodeTypeDetailsContentLogic";
import { useValidation } from "./useNodeTypeDetailsContentLogic";
import {
    useAddElement,
    useIsEditMode,
    useParameterDefinitions,
    useRemoveElement,
    useRenderFieldLabel,
    useSetEditedEdges,
    useSetProperty,
    useVariableTypes,
} from "./useNodeTypeDetailsContentLogic";
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

type NodeDetailsProps = Prettify<NodeTypeDetailsContentProps>;

function NodeDetails({ node, errors, showSwitch, showValidation, edges, onChange }: NodeDetailsProps) {
    const variableTypes = useVariableTypes({ node });
    const parameterDefinitions = useParameterDefinitions({ node });
    const setProperty = useSetProperty({ onChange, node });
    const isEditMode = useIsEditMode({ onChange });
    const setEditedEdges = useSetEditedEdges({ onChange });
    const addElement = useAddElement({ onChange });
    const removeElement = useRemoveElement({ onChange });

    useValidation({ edges, node, showValidation });

    switch (node.type) {
        case "Source":
            return (
                <Source
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            );
        case "Sink":
            return (
                <Sink
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
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
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            );
        case "FragmentInput":
            return (
                <FragmentInput
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            );
        case "Join":
            return (
                <JoinNode
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            );
        case "CustomNode":
            return (
                <CustomNode
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    parameterDefinitions={parameterDefinitions}
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
                    setProperty={setProperty}
                    showValidation={showValidation}
                    variableTypes={variableTypes}
                />
            );
        }
        case "Variable":
            return (
                <Variable
                    addElement={addElement}
                    errors={errors}
                    isEditMode={isEditMode}
                    node={node}
                    removeElement={removeElement}
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
                    setEditedEdges={setEditedEdges}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                    variableTypes={variableTypes}
                />
            );
        case "Split":
            return <Split errors={errors} isEditMode={isEditMode} node={node} setProperty={setProperty} showValidation={showValidation} />;
        default:
            return (
                <NodeDetailsFallback
                    errors={errors}
                    node={node}
                    setProperty={setProperty}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                />
            );
    }
}

export function NodeTypeDetailsContent(props: NodeTypeDetailsContentProps): React.JSX.Element {
    const { errors, showSwitch, onChange, edges, node, showValidation } = props;
    const renderFieldLabel = useRenderFieldLabel({ node });
    return (
        <FieldLabelProvider value={renderFieldLabel}>
            <NodeDetails
                node={node}
                errors={errors}
                edges={edges}
                showSwitch={showSwitch}
                showValidation={showValidation}
                onChange={onChange}
            />
        </FieldLabelProvider>
    );
}
