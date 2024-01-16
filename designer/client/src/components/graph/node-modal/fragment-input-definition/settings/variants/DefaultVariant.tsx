import React from "react";
import { CustomSwitch, SettingLabelStyled, SettingsWrapper } from "./fields/StyledSettingsComponnets";
import { FormControl, FormControlLabel } from "@mui/material";
import { useTranslation } from "react-i18next";
import { DefaultParameterVariant, onChangeType } from "../../item";
import { NodeValidationError, VariableTypes } from "../../../../../../types";
import { TextAreaNodeWithFocus } from "../../../../../withFocus";
import InitialValue from "./fields/InitialValue";
import { ValidationsFields } from "./fields/validation";
import { getValidationErrorsForField } from "../../../editors/Validators";

interface Props {
    item: DefaultParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: NodeValidationError[];
}

export const DefaultVariant = ({ item, onChange, path, variableTypes, readOnly, errors, ...props }: Props) => {
    const { t } = useTranslation();

    return (
        <SettingsWrapper {...props}>
            <FormControl>
                <SettingLabelStyled required>{t("fragment.required", "Required:")}</SettingLabelStyled>
                <FormControlLabel
                    control={<CustomSwitch checked={item.required} onChange={() => onChange(`${path}.required`, !item.required)} />}
                    label=""
                />
            </FormControl>
            <ValidationsFields
                path={path}
                onChange={onChange}
                item={item}
                variableTypes={variableTypes}
                readOnly={readOnly}
                errors={errors}
            />
            <InitialValue
                onChange={onChange}
                item={item}
                path={path}
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
        </SettingsWrapper>
    );
};
