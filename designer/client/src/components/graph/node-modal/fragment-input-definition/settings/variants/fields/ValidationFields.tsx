import React from "react";
import { useTranslation } from "react-i18next";
import { FieldName, onChangeType, ValueCompileTimeValidation } from "../../../item";
import { fieldLabel } from "./StyledSettingsComponnets";
import { NodeValidationError, VariableTypes } from "../../../../../../../types";
import EditableEditor from "../../../../editors/EditableEditor";
import { NodeInput } from "../../../../../../withFocus";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import { FormControl } from "@mui/material";

interface ValidationFields extends ValueCompileTimeValidation {
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    readOnly: boolean;
    errors: NodeValidationError[];
    name: string;
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
}: ValidationFields) {
    const { t } = useTranslation();

    const validationExpressionFieldName: FieldName = `$param.${name}.$validationExpression`;

    return (
        <>
            <EditableEditor
                fieldLabel={t("fragment.validation.validationExpression", "Validation expression:")}
                renderFieldLabel={() =>
                    fieldLabel({ label: t("fragment.validation.validationExpression", "Validation expression:"), required: true })
                }
                expressionObj={validationExpression}
                onValueChange={(value) => onChange(`${path}.valueCompileTimeValidation.validationExpression.expression`, value)}
                variableTypes={variableTypes}
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
                <NodeInput
                    style={{ width: "70%" }}
                    value={validationFailedMessage}
                    onChange={(event) =>
                        onChange(
                            `${path}.valueCompileTimeValidation.validationFailedMessage`,
                            event.currentTarget.value === "" ? null : event.currentTarget.value,
                        )
                    }
                    readOnly={readOnly}
                    placeholder={t("fragment.validation.validationErrorMessagePlaceholder", "eg. Parameter value is not valid.")}
                />
            </FormControl>
        </>
    );
}
