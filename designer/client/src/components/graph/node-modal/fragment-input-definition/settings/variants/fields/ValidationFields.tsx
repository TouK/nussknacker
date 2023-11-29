import React from "react";
import { useTranslation } from "react-i18next";
import { FieldName, FragmentValidation, onChangeType } from "../../../item";
import { SettingRow, fieldLabel } from "./StyledSettingsComponnets";
import { VariableTypes } from "../../../../../../../types";
import EditableEditor from "../../../../editors/EditableEditor";
import { NodeInput } from "../../../../../../withFocus";
import { errorValidator, Error } from "../../../../editors/Validators";
import { EditorType } from "../../../../editors/expression/Editor";

interface ValidationFields extends Omit<FragmentValidation["validationExpression"], "validation" | "language"> {
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    readOnly: boolean;
    fieldsErrors: Error[];
    validationExpressionFieldName: FieldName;
}

export default function ValidationFields({
    expression,
    failedMessage,
    variableTypes,
    path,
    onChange,
    readOnly,
    fieldsErrors,
    validationExpressionFieldName,
}: ValidationFields) {
    const { t } = useTranslation();

    return (
        <>
            <EditableEditor
                fieldName={validationExpressionFieldName}
                fieldLabel={t("fragment.validation.validationExpression", "Validation expression:")}
                renderFieldLabel={() => fieldLabel(t("fragment.validation.validationExpression", "Validation expression:"))}
                expressionObj={{ language: expression.language, expression: expression.expression }}
                onValueChange={(value) => onChange(`${path}.validationExpression.expression.expression`, value)}
                variableTypes={variableTypes}
                readOnly={readOnly}
                errors={fieldsErrors}
                param={{ validators: [errorValidator], editor: { type: EditorType.RAW_PARAMETER_EDITOR } }}
                showValidation
            />
            <SettingRow>
                {fieldLabel(t("fragment.validation.validationErrorMessage", "Validation error message:"))}
                <NodeInput
                    style={{ width: "70%" }}
                    value={failedMessage}
                    onChange={(event) => onChange(`${path}.validationExpression.errorMessage`, event.currentTarget.value)}
                    readOnly={readOnly}
                />
            </SettingRow>
        </>
    );
}
