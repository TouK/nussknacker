import { isEmpty, isEqual } from "lodash";
import React, { useMemo } from "react";

import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { useAppSelector } from "../../../store/storeHelpers";
import type { UIParameter } from "../../../types/definition";
import type { Edge } from "../../../types/edge";
import { EdgeKind } from "../../../types/edge";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../types/validation";
import { DescriptionField } from "./DescriptionField";
import { EdgesDndComponent } from "./EdgesDndComponent";
import { FieldType } from "./editors/field/Field";
import { getValidationErrorsForField } from "./editors/Validators";
import { NameField } from "./NameField";
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
    setEditedEdges,
    setProperty,
    showSwitch,
    showValidation,
    variableTypes,
}: Props): React.JSX.Element {
    const processDefinitionData = useAppSelector(getProcessDefinitionData);
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
    const edgeTypes = useMemo(
        () => [
            {
                value: EdgeKind.switchNext,
            },
            {
                value: EdgeKind.switchDefault,
                onlyOne: true,
                disabled: true,
            },
        ],
        [],
    );
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
            <NameField isEditMode={isEditMode} showValidation={showValidation} node={node} setProperty={setProperty} errors={errors} />
            {showExpression ? (
                <StaticExpressionField
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    showSwitch={showSwitch}
                    node={node}
                    variableTypes={variableTypes}
                    parameterDefinitions={parameterDefinitions}
                    errors={errors}
                    setProperty={setProperty}
                    fieldLabel={"Expression (deprecated)"}
                />
            ) : null}
            {showExprVal ? (
                <NodeField
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    node={node}
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
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
