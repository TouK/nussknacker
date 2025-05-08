import React, { useCallback } from "react";
import { useSelector } from "react-redux";

import ProcessUtils from "../../../common/ProcessUtils";
import type { RootState } from "../../../reducers";
import type { NodeType, NodeValidationError, TypedObjectTypingResult, TypingInfo, TypingResult, VariableTypes } from "../../../types";
import { DescriptionField } from "./DescriptionField";
import EditableEditor from "./editors/EditableEditor";
import type { ExpressionObj } from "./editors/expression/types";
import LabeledInput from "./editors/field/LabeledInput";
import { getValidationErrorsForField } from "./editors/Validators";
import { IdField } from "./IdField";
import { getExpressionType, getNodeTypingInfo } from "./NodeDetailsContent/selectors";
import { useDiffMark } from "./PathsToMark";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

const DEFAULT_EXPRESSION_ID = "$expression";

function getTypingResult(expressionType: TypingResult, nodeTypingInfo: TypingInfo): TypedObjectTypingResult | TypingResult {
    return expressionType || nodeTypingInfo?.[DEFAULT_EXPRESSION_ID];
}

interface Props {
    isEditMode?: boolean;
    node: NodeType;
    setProperty: SetProperty;
    showValidation: boolean;
    errors: NodeValidationError[];
    showSwitch?: boolean;
    variableTypes: VariableTypes;
    renderFieldLabel: (paramName: string) => JSX.Element;
}

export default function Variable({
    node,
    setProperty,
    isEditMode,
    showValidation,
    errors,
    variableTypes,
    renderFieldLabel,
}: Props): JSX.Element {
    const onExpressionChange = useCallback((value: ExpressionObj) => setProperty("value.expression", value.expression), [setProperty]);
    const [isMarked] = useDiffMark();
    const inferredVariableType = useSelector((state: RootState) => {
        const expressionType = getExpressionType(state)(node.id);
        const nodeTypingInfo = getNodeTypingInfo(state)(node.id);
        const varExprType = getTypingResult(expressionType, nodeTypingInfo);
        return ProcessUtils.humanReadableType(varExprType);
    });
    const readOnly = !isEditMode;

    return (
        <>
            <IdField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
            <LabeledInput
                value={node.varName}
                onChange={(event) => setProperty("varName", event.target.value)}
                isMarked={isMarked("varName")}
                readOnly={readOnly}
                showValidation={showValidation}
                fieldErrors={getValidationErrorsForField(errors, "varName")}
            >
                {renderFieldLabel("Variable Name")}
            </LabeledInput>
            <EditableEditor
                fieldLabel={"Expression"}
                renderFieldLabel={renderFieldLabel}
                expressionObj={node.value}
                onValueChange={onExpressionChange}
                readOnly={readOnly}
                showValidation={showValidation}
                showSwitch={false}
                fieldErrors={getValidationErrorsForField(errors, "$expression")}
                variableTypes={variableTypes}
                validationLabelInfo={inferredVariableType}
            />
            <DescriptionField
                isEditMode={!readOnly}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
