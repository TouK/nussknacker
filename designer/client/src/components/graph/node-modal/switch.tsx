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
import React, { memo, useEffect, useMemo } from "react";
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
    const definition = useMemo(
        () => processDefinitionData.componentGroups?.flatMap((g) => g.components).find((c) => c.node.type === node.type)?.node,
        [node.type, processDefinitionData.componentGroups],
    );
    const currentExpression = useMemo(() => node.expression, [node.expression]);
    const currentExprVal = useMemo(() => node.exprVal, [node.exprVal]);
    const fieldErrors = useMemo(() => getValidationErrorsForField(errors, "exprVal"), [errors]);
    const showExpression = useMemo(
        () => (definition["expression"] ? !isEqual(definition["expression"], currentExpression) : currentExpression?.expression),
        [currentExpression, definition],
    );
    const showExprVal = useMemo(
        () => (!isEmpty(fieldErrors) || definition["exprVal"] ? definition["exprVal"] !== currentExprVal : currentExprVal),
        [currentExprVal, definition, fieldErrors],
    );
    const [, isCompareView] = useDiffMark();

    const getExpressionType = useSelector(getNodeExpressionType);
    const nodeExpressionType = useMemo(() => getExpressionType(node.id), [getExpressionType, node.id]);
    const edgeTypes = useMemo(() => {
        return [
            {
                value: EdgeKind.switchNext,
            },
            {
                value: EdgeKind.switchDefault,
                onlyOne: true,
                disabled: true,
            },
        ];
    }, []);
    const types = useMemo(
        () =>
            node.exprVal
                ? {
                      ...variableTypes,
                      [node.exprVal]: nodeExpressionType,
                  }
                : variableTypes,
        [node.exprVal, nodeExpressionType, variableTypes],
    );
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
                    edgeTypes={edgeTypes}
                    ordered
                    readOnly={!isEditMode}
                    variableTypes={types}
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
