import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { getProcessDefinitionData } from "../../../../../../../reducers/selectors/getProcessDefinitionData";
import { useAppSelector } from "../../../../../../../store/storeHelpers";
import type { ReturnedType } from "../../../../../../../types/scenarioGraph";
import type { NodeValidationError, VariableTypes } from "../../../../../../../types/validation";
import EditableEditor from "../../../../editors/EditableEditor";
import Input from "../../../../editors/field/Input";
import { FormControl } from "../../../../editors/FormControl";
import { FieldLabelProvider } from "../../../../editors/RenderFieldLabel";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import { nodeValue } from "../../../../NodeDetailsContent/NodeTableStyled";
import type { FieldName, onChangeType, ValueCompileTimeValidation } from "../../../item/types";
import { FieldLabel } from "./StyledSettingsComponnets";

interface ValidationFields extends ValueCompileTimeValidation {
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    readOnly: boolean;
    errors: NodeValidationError[];
    name: string;
    typ: ReturnedType;
    isValidating: boolean;
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
    isValidating,
}: ValidationFields) {
    const { t } = useTranslation();
    const definitionData = useAppSelector(getProcessDefinitionData);

    const validationExpressionFieldName: FieldName = `$param.${name}.$validationExpression`;

    const extendedVariableType = useMemo(
        () => ({
            ...variableTypes,
            value: definitionData.classes.find((typesInformationType) => typesInformationType.refClazzName === typ.refClazzName),
        }),
        [definitionData.classes, typ.refClazzName, variableTypes],
    );

    const renderFieldLabel = () => <FieldLabel label={t("fragment.validation.validationExpression", "Validation expression:")} required />;
    return (
        <>
            <FieldLabelProvider value={renderFieldLabel}>
                <EditableEditor
                    fieldLabel={t("fragment.validation.validationExpression", "Validation expression:")}
                    expressionObj={validationExpression}
                    onValueChange={(value) =>
                        onChange(`${path}.valueCompileTimeValidation.validationExpression.expression`, value.expression)
                    }
                    variableTypes={extendedVariableType}
                    readOnly={readOnly}
                    fieldErrors={getValidationErrorsForField(errors, validationExpressionFieldName)}
                    showValidation
                    showSwitch
                    isValidating={isValidating}
                />
            </FieldLabelProvider>
            <FormControl>
                <FieldLabel
                    label={t("fragment.validation.validationErrorMessage", "Validation error message:")}
                    hintText={t(
                        "fragment.validation.validationErrorMessageHintText",
                        "An empty value means that the validation error message will be generated dynamically based on the validation expression.",
                    )}
                />
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
                        placeholder={t("fragment.validation.validationErrorMessagePlaceholder", "e.g. Parameter value is not valid.")}
                        fieldErrors={[]}
                    />
                </div>
            </FormControl>
        </>
    );
}
