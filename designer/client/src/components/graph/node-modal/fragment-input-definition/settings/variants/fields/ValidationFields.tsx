import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { FieldName, onChangeType, ValueCompileTimeValidation } from "../../../item";
import { fieldLabel } from "./StyledSettingsComponnets";
import { NodeValidationError, ReturnedType, VariableTypes } from "../../../../../../../types";
import EditableEditor from "../../../../editors/EditableEditor";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import { FormControl } from "@mui/material";
import { useSelector } from "react-redux";
import { getProcessDefinitionData } from "../../../../../../../reducers/selectors/settings";
import { nodeValue } from "../../../../NodeDetailsContent/NodeTableStyled";
import Input from "../../../../editors/field/Input";

interface ValidationFields extends ValueCompileTimeValidation {
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    readOnly: boolean;
    errors: NodeValidationError[];
    name: string;
    typ: ReturnedType;
}

export default function ValidationFields({
    validationExpression,
    validationFailedMessage,
    variableTypes,
    path,
    onChange,
    readOnly,
    errors,
    name,
    typ,
}: ValidationFields) {
    const { t } = useTranslation();
    const definitionData = useSelector(getProcessDefinitionData);

    const validationExpressionFieldName: FieldName = `$param.${name}.$validationExpression`;

    const extendedVariableType = useMemo(
        () => ({
            ...variableTypes,
            value: definitionData.classes.find((typesInformationType) => typesInformationType.refClazzName === typ.refClazzName),
        }),
        [definitionData.classes, typ.refClazzName, variableTypes],
    );

    return (
        <>
            <EditableEditor
                fieldLabel={t("fragment.validation.validationExpression", "Validation expression:")}
                renderFieldLabel={() =>
                    fieldLabel({ label: t("fragment.validation.validationExpression", "Validation expression:"), required: true })
                }
                expressionObj={validationExpression}
                onValueChange={(value) => onChange(`${path}.valueCompileTimeValidation.validationExpression.expression`, value)}
                variableTypes={extendedVariableType}
                readOnly={readOnly}
                fieldErrors={getValidationErrorsForField(errors, validationExpressionFieldName)}
                showValidation
            />
            <FormControl>
                {fieldLabel({
                    label: t("fragment.validation.validationErrorMessage", "Validation error message:"),
                    hintText: t(
                        "fragment.validation.validationErrorMessageHintText",
                        "An empty value means that the validation error message will be generated dynamically based on the validation expression.",
                    ),
                })}
                <div className={nodeValue}>
                    <Input
                        value={validationFailedMessage}
                        onChange={(event) =>
                            onChange(
                                `${path}.valueCompileTimeValidation.validationFailedMessage`,
                                event.currentTarget.value === "" ? null : event.currentTarget.value,
                            )
                        }
                        readOnly={readOnly}
                        placeholder={t("fragment.validation.validationErrorMessagePlaceholder", "eg. Parameter value is not valid.")}
                        fieldErrors={[]}
                    />
                </div>
            </FormControl>
        </>
    );
}
