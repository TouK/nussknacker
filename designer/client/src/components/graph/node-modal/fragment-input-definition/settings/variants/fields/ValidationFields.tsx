import { FormControlLabel } from "@mui/material";
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
import { CustomSwitch, FieldLabel, SettingLabelStyled } from "./StyledSettingsComponnets";

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
    strictTypeChecking,
    variableTypes,
    path,
    onChange,
    readOnly,
    errors,
    name,
    typ,
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
            <FormControl>
                <FieldLabel
                    label={t("fragment.validation.strictTypeChecking", "Strict type checking:")}
                    hintText={t(
                        "fragment.validation.strictTypeCheckingHint",
                        "When enabled, the expression result type must match the expected parameter type. If the result type is unknown, it will be marked as a validation error — unless the parameter type explicitly allows unknown.",
                    )}
                />
                <FormControlLabel
                    control={
                        <CustomSwitch
                            checked={strictTypeChecking}
                            onChange={(event) =>
                                onChange(`${path}.valueCompileTimeValidation.strictTypeChecking`, event.currentTarget.checked)
                            }
                            readOnly={!readOnly}
                        />
                    }
                    label=""
                />
            </FormControl>
        </>
    );
}
