import React from "react";
import { useTranslation } from "react-i18next";
import { FragmentValidation, onChangeType } from "../../../item";
import { SettingRow, fieldLabel } from "./StyledSettingsComponnets";
import { NodeValidationError, VariableTypes } from "../../../../../../../types";
import EditableEditor from "../../../../editors/EditableEditor";
import { NodeInput } from "../../../../../../withFocus";
import { getValidationErrorsForField } from "../../../../editors/Validators";

interface ValidationFields extends Omit<FragmentValidation["validationExpression"], "validation" | "language"> {
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    readOnly: boolean;
    errors: NodeValidationError[];
}

export default function ValidationFields({ expression, failedMessage, variableTypes, path, onChange, readOnly, errors }: ValidationFields) {
    const { t } = useTranslation();

    return (
        <>
            <EditableEditor
                fieldLabel={t("fragment.validation.validationExpression", "Validation expression:")}
                renderFieldLabel={() => fieldLabel(t("fragment.validation.validationExpression", "Validation expression:"))}
                expressionObj={{ language: expression.language, expression: expression.expression }}
                onValueChange={(value) => onChange(`${path}.validationExpression.expression.expression`, value)}
                variableTypes={variableTypes}
                readOnly={readOnly}
                fieldErrors={getValidationErrorsForField(errors, "validationExpression")}
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
