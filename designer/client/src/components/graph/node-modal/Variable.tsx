import React, { useCallback } from "react";
import EditableEditor from "./editors/EditableEditor";
import LabeledInput from "./editors/field/LabeledInput";
import LabeledTextarea from "./editors/field/LabeledTextarea";
import { NodeType, NodeValidationError, TypedObjectTypingResult, TypingInfo, TypingResult, VariableTypes } from "../../../types";
import { useDiffMark } from "./PathsToMark";
import { useSelector } from "react-redux";
import { RootState } from "../../../reducers";
import { getExpressionType, getNodeTypingInfo } from "./NodeDetailsContent/selectors";
import ProcessUtils from "../../../common/ProcessUtils";
import { IdField } from "./IdField";
import { getValidationErrorsForField } from "./editors/Validators";

const DEFAULT_EXPRESSION_ID = "$expression";

function getTypingResult(expressionType: TypingResult, nodeTypingInfo: TypingInfo): TypedObjectTypingResult | TypingResult {
    return expressionType || nodeTypingInfo?.[DEFAULT_EXPRESSION_ID];
}

interface Props {
    isEditMode?: boolean;
    node: NodeType;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
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
    const onExpressionChange = useCallback((value: string) => setProperty("value.expression", value), [setProperty]);
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
            <LabeledTextarea
                value={node?.additionalFields?.description || ""}
                onChange={(event) => setProperty("additionalFields.description", event.target.value)}
                isMarked={isMarked("additionalFields.description")}
                readOnly={readOnly}
                className={"node-input"}
                fieldErrors={getValidationErrorsForField(errors, "additionalFields.description")}
            >
                {renderFieldLabel("Description")}
            </LabeledTextarea>
        </>
    );
}
