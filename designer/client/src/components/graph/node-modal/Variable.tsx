import React, { useCallback } from "react";

import ProcessUtils from "../../../common/ProcessUtils";
import type { RootState } from "../../../reducers";
import { useAppSelector } from "../../../store/storeHelpers";
import type { TypedObjectTypingResult, TypingInfo, TypingResult } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../types/validation";
import { DescriptionField } from "./DescriptionField";
import EditableEditor from "./editors/EditableEditor";
import type { ExpressionObj } from "./editors/expression/types";
import { EditorType } from "./editors/expression/types";
import LabeledInput from "./editors/field/LabeledInput";
import { FieldLabelConsumer } from "./editors/RenderFieldLabel";
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
}

export default function Variable({ node, setProperty, isEditMode, showValidation, errors, variableTypes }: Props): React.JSX.Element {
    const onExpressionChange = useCallback((value: ExpressionObj) => setProperty("value", value), [setProperty]);
    const [isMarked] = useDiffMark();
    const inferredVariableType = useAppSelector((state: RootState) => {
        const expressionType = getExpressionType(state)(node.id);
        const nodeTypingInfo = getNodeTypingInfo(state)(node.id);
        const varExprType = getTypingResult(expressionType, nodeTypingInfo);
        return ProcessUtils.humanReadableType(varExprType);
    });
    const readOnly = !isEditMode;

    return (
        <>
            <IdField node={node} isEditMode={isEditMode} showValidation={showValidation} setProperty={setProperty} errors={errors} />
            <LabeledInput
                value={node.varName}
                onChange={(event) => setProperty("varName", event.target.value)}
                isMarked={isMarked("varName")}
                readOnly={readOnly}
                showValidation={showValidation}
                fieldErrors={getValidationErrorsForField(errors, "varName")}
            >
                <FieldLabelConsumer text="Variable Name" />
            </LabeledInput>
            <EditableEditor
                editors={[
                    { type: EditorType.SPEL_PARAMETER_EDITOR },
                    { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR },
                    { type: EditorType.JSON_TEMPLATE_PARAMETER_EDITOR },
                ]}
                fieldLabel={"Expression"}
                expressionObj={node.value}
                onValueChange={onExpressionChange}
                readOnly={readOnly}
                showValidation={showValidation}
                fieldErrors={getValidationErrorsForField(errors, "$expression")}
                variableTypes={variableTypes}
                validationLabelInfo={inferredVariableType}
            />
            <DescriptionField
                isEditMode={!readOnly}
                showValidation={showValidation}
                node={node}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
