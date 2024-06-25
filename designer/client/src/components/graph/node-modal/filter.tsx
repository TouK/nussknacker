import { Edge, EdgeKind, NodeType, NodeValidationError, UIParameter, VariableTypes } from "../../../types";
import { useDiffMark } from "./PathsToMark";
import { IdField } from "./IdField";
import { StaticExpressionField } from "./StaticExpressionField";
import { DisableField } from "./DisableField";
import { EdgesDndComponent } from "./EdgesDndComponent";
import { DescriptionField } from "./DescriptionField";
import React from "react";

export function Filter({
    edges,
    errors,
    variableTypes,
    isEditMode,
    node,
    parameterDefinitions,
    renderFieldLabel,
    setEditedEdges,
    setProperty,
    showSwitch,
    showValidation,
}: {
    edges: Edge[];
    errors: NodeValidationError[];
    variableTypes?: VariableTypes;
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    renderFieldLabel: (paramName: string) => JSX.Element;
    setEditedEdges: (edges: Edge[]) => void;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showSwitch?: boolean;
    showValidation?: boolean;
}): JSX.Element {
    const [, isCompareView] = useDiffMark();
    return (
        <>
            <IdField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                setProperty={setProperty}
                renderFieldLabel={renderFieldLabel}
                errors={errors}
            />
            <StaticExpressionField
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                fieldLabel={"Expression"}
                parameterDefinitions={parameterDefinitions}
                showSwitch={showSwitch}
                variableTypes={variableTypes}
                showValidation={showValidation}
                errors={errors}
                isEditMode={isEditMode}
                node={node}
            />
            <DisableField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
            {!isCompareView ? (
                <EdgesDndComponent
                    label={"Outputs"}
                    nodeId={node.id}
                    value={edges}
                    onChange={setEditedEdges}
                    edgeTypes={[
                        { value: EdgeKind.filterTrue, onlyOne: true },
                        { value: EdgeKind.filterFalse, onlyOne: true },
                    ]}
                    readOnly={!isEditMode}
                    errors={errors}
                />
            ) : null}
            <DescriptionField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
