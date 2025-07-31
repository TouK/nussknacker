import { isEmpty, isEqual } from "lodash";
import React, { useMemo } from "react";

import { useAppSelector } from "../../../store/configureStore";
import type { Edge, NodeType, NodeValidationError, ProcessDefinitionData, UIParameter, VariableTypes } from "../../../types";
import { EdgeKind } from "../../../types";
import { DescriptionField } from "./DescriptionField";
import { EdgesDndComponent } from "./EdgesDndComponent";
import { FieldType } from "./editors/field/Field";
import { getValidationErrorsForField } from "./editors/Validators";
import { IdField } from "./IdField";
import { getNodeExpressionType } from "./NodeDetailsContent/selectors";
import { NodeField } from "./NodeField";
import { useDiffMark } from "./PathsToMark";
import { StaticExpressionField } from "./StaticExpressionField";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface Props {
    edges: Edge[];
    errors?: NodeValidationError[];
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    processDefinitionData?: ProcessDefinitionData;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setEditedEdges: (edges: Edge[]) => void;
    setProperty: SetProperty;
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

    const getExpressionType = useAppSelector(getNodeExpressionType);
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
