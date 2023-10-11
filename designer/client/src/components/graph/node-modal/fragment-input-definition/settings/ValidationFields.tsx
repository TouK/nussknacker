import React from "react";
import { useTranslation } from "react-i18next";
import { FragmentValidation, onChangeType } from "../item";
import { fieldLabel } from "./StyledSettingsComponnets";
import { ExpressionLang } from "../../editors/expression/types";
import { VariableTypes } from "../../../../../types";
import EditableEditor from "../../editors/EditableEditor";

interface ValidationFields extends Omit<FragmentValidation, "validation"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
}

export default function ValidationFields({ validatioErrorMessage, validationExpression, variableTypes, path, onChange }: ValidationFields) {
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

            <EditableEditor
                fieldName="validationErrorMessage"
                fieldLabel={t("fragment.validation.validationErrorMessage", "Validation error message:")}
                renderFieldLabel={() => fieldLabel(t("fragment.validation.validationErrorMessage", "Validation error message:"))}
                expressionObj={{ language: ExpressionLang.SpEL, expression: validatioErrorMessage }}
                onValueChange={(value) => onChange(`${path}.validatioErrorMessage`, value)}
                variableTypes={variableTypes}
            />
        </>
    );
}
