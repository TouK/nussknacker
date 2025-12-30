import React, { useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";

import type { ReturnedType } from "../../../../../../../types/scenarioGraph";
import type { NodeValidationError, VariableTypes } from "../../../../../../../types/validation";
import ValidationLabels from "../../../../../../modals/ValidationLabels";
import { CollectionField } from "../../../../aggregate/groupBy/collectionField";
import { FormControl } from "../../../../editors/FormControl";
import { getValidationErrorsForField, mandatoryValueValidator } from "../../../../editors/Validators";
import { NodeValue } from "../../../../node/NodeValue";
import type { FixedValuesOption, onChangeType } from "../../../item/types";
import { SettingLabelStyled } from "./StyledSettingsComponnets";

interface Props {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesList: FixedValuesOption[];
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: NodeValidationError[];
    typ: ReturnedType;
    name: string;
    initialValue: FixedValuesOption;
    inputLabel: string;
}

function repeated(arr: string[]): string[] {
    const seen = {};
    return arr.filter((v) => {
        if (!seen[v]) {
            seen[v] = 1;
            return false;
        }
        if (seen[v] === 1) {
            seen[v] += 1;
            return true;
        }
        return false;
    });
}

export const UserDefinedListInput = ({
    fixedValuesList,
    path,
    onChange,
    variableTypes,
    readOnly,
    errors,
    typ,
    name,
    initialValue,
    inputLabel,
}: Props) => {
    const { t } = useTranslation();
    const value = fixedValuesList.map(({ expression }) => expression);

    const fieldName = `$param.${name}.$fixedValuesList`;
    const errorsForField = useMemo(() => {
        if (!mandatoryValueValidator.isValid(fixedValuesList)) {
            return [
                {
                    errorType: "SaveAllowed",
                    fieldName,
                    typ: typ.refClazzName,
                    description: mandatoryValueValidator.description(),
                    message: mandatoryValueValidator.message(),
                },
            ];
        }
        return [
            ...getValidationErrorsForField(errors, fieldName),
            ...repeated(fixedValuesList.map(({ expression }) => expression)).map((value) => ({
                errorType: "SaveAllowed",
                fieldName,
                typ: typ.refClazzName,
                message: t("uniqueValuesValidator.message", "Value {{value}} is not unique", { value }),
                description: t("validator.uniqueValues.description", "Please fill field with unique values"),
            })),
        ];
    }, [errors, fieldName, fixedValuesList, t, typ.refClazzName]);

    useEffect(() => {
        if (initialValue && !fixedValuesList.find(({ label }) => label === initialValue?.label)) {
            onChange(`${path}.initialValue`, null);
        }
    }, [fixedValuesList, initialValue, onChange, path]);

    return (
        <FormControl>
            <SettingLabelStyled>{inputLabel}</SettingLabelStyled>
            <NodeValue>
                <CollectionField
                    value={value}
                    onChange={(values) => {
                        onChange(
                            `${path}.valueEditor.fixedValuesList`,
                            values.map((expression) => ({ expression, label: expression })),
                        );
                    }}
                    variableTypes={variableTypes}
                    disabled={readOnly}
                    isValueValid={(value) =>
                        !errorsForField.find(({ message, description }) => message.includes(value) || description.includes(value))
                    }
                />
                <ValidationLabels fieldErrors={errorsForField} />
            </NodeValue>
        </FormControl>
    );
};
