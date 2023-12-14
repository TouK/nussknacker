import React from "react";
import { useTranslation } from "react-i18next";
import { FragmentValidation, onChangeType } from "../../../item";
import { SettingRow, fieldLabel } from "./StyledSettingsComponnets";
import { ExpressionLang } from "../../../../editors/expression/types";
import { NodeValidationError, VariableTypes } from "../../../../../../../types";
import EditableEditor from "../../../../editors/EditableEditor";
import { NodeInput } from "../../../../../../withFocus";
import { getValidationErrorsForField } from "../../../../editors/Validators";

interface ValidationFields extends Omit<FragmentValidation, "validation"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: NodeValidationError[];
}

export default function ValidationFields({
    validationErrorMessage,
    validationExpression,
    variableTypes,
    path,
    onChange,
    readOnly,
    errors,
}: ValidationFields) {
    const { t } = useTranslation();
    return (
        <>
            <EditableEditor
                fieldLabel={t("fragment.validation.validationExpression", "Validation expression:")}
                renderFieldLabel={() => fieldLabel(t("fragment.validation.validationExpression", "Validation expression:"))}
                expressionObj={{ language: ExpressionLang.SpEL, expression: validationExpression }}
                onValueChange={(value) => onChange(`${path}.validationExpression`, value)}
                variableTypes={variableTypes}
                readOnly={readOnly}
                fieldErrors={getValidationErrorsForField(errors, "validationExpression")}
                showValidation
            />
            <SettingRow>
                {fieldLabel(t("fragment.validation.validationErrorMessage", "Validation error message:"))}
                <NodeInput
                    style={{ width: "70%" }}
                    value={validationErrorMessage}
                    onChange={(event) => onChange(`${path}.validationErrorMessage`, event.currentTarget.value)}
                    readOnly={readOnly}
                />
            </SettingRow>
        </>
    );
}
