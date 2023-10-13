import React from "react";
import { useTranslation } from "react-i18next";
import { FragmentValidation, onChangeType } from "../item";
import { SettingRow, fieldLabel } from "./StyledSettingsComponnets";
import { ExpressionLang } from "../../editors/expression/types";
import { VariableTypes } from "../../../../../types";
import EditableEditor from "../../editors/EditableEditor";
import { NodeInput } from "../../../../withFocus";

interface ValidationFields extends Omit<FragmentValidation, "validation"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
}

export default function ValidationFields({
    validationErrorMessage,
    validationExpression,
    variableTypes,
    path,
    onChange,
}: ValidationFields) {
    const { t } = useTranslation();
    return (
        <>
            <EditableEditor
                fieldName="validationExpression"
                fieldLabel={t("fragment.validation.validationExpression", "Validation expression:")}
                renderFieldLabel={() => fieldLabel(t("fragment.validation.validationExpression", "Validation expression:"))}
                expressionObj={{ language: ExpressionLang.SpEL, expression: validationExpression }}
                onValueChange={(value) => onChange(`${path}.validationExpression`, value)}
                variableTypes={variableTypes}
            />
            <SettingRow>
                {fieldLabel(t("fragment.validation.validationErrorMessage", "Validation error message:"))}
                <NodeInput
                    style={{ width: "70%" }}
                    value={validationErrorMessage}
                    onChange={(event) => onChange(`${path}.validationErrorMessage`, event.currentTarget.value)}
                />
            </SettingRow>
        </>
    );
}
