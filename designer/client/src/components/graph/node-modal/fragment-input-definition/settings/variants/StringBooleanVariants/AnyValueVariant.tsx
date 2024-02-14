import React from "react";
import InitialValue from "../fields/InitialValue";
import { SettingLabelStyled } from "../fields/StyledSettingsComponnets";
import { TextAreaNodeWithFocus } from "../../../../../../withFocus";
import { AnyValueParameterVariant, onChangeType } from "../../../item";
import { NodeValidationError, VariableTypes } from "../../../../../../../types";
import { useTranslation } from "react-i18next";
import { ValidationsFields } from "../fields/validation";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import { FormControl } from "@mui/material";

interface Props {
    item: AnyValueParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: NodeValidationError[];
}

export const AnyValueVariant = ({ item, path, onChange, readOnly, variableTypes, errors }: Props) => {
    const { t } = useTranslation();

    return (
        <>
            <ValidationsFields
                path={path}
                item={item}
                onChange={onChange}
                variableTypes={variableTypes}
                readOnly={readOnly}
                errors={errors}
            />
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
                readOnly={readOnly}
                variableTypes={variableTypes}
                fieldErrors={getValidationErrorsForField(errors, `$param.${item.name}.$initialValue`)}
            />
            <FormControl>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNodeWithFocus
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                    disabled={readOnly}
                    className={"node-input"}
                />
            </FormControl>
        </>
    );
};
