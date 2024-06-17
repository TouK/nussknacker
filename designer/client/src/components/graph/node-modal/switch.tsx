import { Edge, EdgeKind, NodeType, NodeValidationError, ProcessDefinitionData, UIParameter, VariableTypes } from "../../../types";
import { getValidationErrorsForField } from "./editors/Validators";
import { isEmpty, isEqual } from "lodash";
import { useDiffMark } from "./PathsToMark";
import { useSelector } from "react-redux";
import { RootState } from "../../../reducers";
import { IdField } from "./IdField";
import { StaticExpressionField } from "./StaticExpressionField";
import { NodeField } from "./NodeField";
import { FieldType } from "./editors/field/Field";
import { EdgesDndComponent } from "./EdgesDndComponent";
import { DescriptionField } from "./DescriptionField";
import React from "react";
import { getNodeExpressionType } from "./NodeDetailsContent/selectors";

interface Props {
    edges: Edge[];
    errors?: NodeValidationError[];
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    processDefinitionData?: ProcessDefinitionData;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setEditedEdges: (edges: Edge[]) => void;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showSwitch?: boolean;
    showValidation?: boolean;
    variableTypes?: VariableTypes;
}

export function Switch({
    edges,
    errors = [],
    isEditMode,
    node,
    parameterDefinitions,
    processDefinitionData,
    renderFieldLabel,
    setEditedEdges,
    setProperty,
    showSwitch,
    showValidation,
    variableTypes,
}: Props): JSX.Element {
    const definition = processDefinitionData.componentGroups?.flatMap((g) => g.components).find((c) => c.node.type === node.type)?.node;
    const currentExpression = node["expression"];
    const currentExprVal = node["exprVal"];
    const fieldErrors = getValidationErrorsForField(errors, "exprVal");
    const showExpression = definition["expression"] ? !isEqual(definition["expression"], currentExpression) : currentExpression?.expression;
    const showExprVal = !isEmpty(fieldErrors) || definition["exprVal"] ? definition["exprVal"] !== currentExprVal : currentExprVal;
    const [, isCompareView] = useDiffMark();

    const nodeExpressionType = useSelector((state: RootState) => getNodeExpressionType(state)(node.id));

    return (
        <>
            <IdField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
            {showExpression ? (
                <StaticExpressionField
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    showSwitch={showSwitch}
                    node={node}
                    variableTypes={variableTypes}
                    parameterDefinitions={parameterDefinitions}
                    errors={errors}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    fieldLabel={"Expression (deprecated)"}
                />
            ) : null}
            {showExprVal ? (
                <NodeField
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    node={node}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    fieldType={FieldType.input}
                    fieldLabel={"exprVal (deprecated)"}
                    fieldName={"exprVal"}
                    errors={errors}
                />
            ) : null}
            {!isCompareView ? (
                <EdgesDndComponent
                    label={"Conditions"}
                    nodeId={node.id}
                    value={edges}
                    onChange={setEditedEdges}
                    edgeTypes={[{ value: EdgeKind.switchNext }, { value: EdgeKind.switchDefault, onlyOne: true, disabled: true }]}
                    ordered
                    readOnly={!isEditMode}
                    variableTypes={
                        node["exprVal"]
                            ? {
                                  ...variableTypes,
                                  [node["exprVal"]]: nodeExpressionType,
                              }
                            : variableTypes
                    }
                    errors={errors}
                />
            ) : null}
            <DescriptionField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
