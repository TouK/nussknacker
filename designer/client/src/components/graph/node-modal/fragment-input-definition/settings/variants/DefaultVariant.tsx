import React from "react";
import { CustomSwitch, SettingLabelStyled, SettingRow, SettingsWrapper } from "./fields/StyledSettingsComponnets";
import { FormControlLabel } from "@mui/material";
import { useTranslation } from "react-i18next";
import { DefaultParameterVariant, onChangeType } from "../../item";
import { VariableTypes } from "../../../../../../types";
import { TextAreaNodeWithFocus } from "../../../../../withFocus";
import InitialValue from "./fields/InitialValue";
import { Error } from "../../../editors/Validators";
import { ValidationsFields } from "./fields/validation";

interface Props {
    item: DefaultParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
    fieldsErrors: Error[];
}

export const DefaultVariant = ({ item, onChange, path, variableTypes, readOnly, fieldsErrors, ...props }: Props) => {
    const { t } = useTranslation();

    return (
        <SettingsWrapper {...props}>
            <SettingRow>
                <SettingLabelStyled required>{t("fragment.required", "Required:")}</SettingLabelStyled>
                <FormControlLabel
                    control={<CustomSwitch checked={item.required} onChange={() => onChange(`${path}.required`, !item.required)} />}
                    label=""
                />
            </SettingRow>
            <ValidationsFields
                path={path}
                onChange={onChange}
                item={item}
                variableTypes={variableTypes}
                readOnly={readOnly}
                fieldErrors={fieldsErrors}
            />
            <InitialValue
                onChange={onChange}
                item={item}
                path={path}
                readOnly={readOnly}
                variableTypes={variableTypes}
                fieldsErrors={fieldsErrors}
                fieldName={`$param.${item.name}.$initialValue`}
            />
            <SettingRow>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNodeWithFocus
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                    disabled={readOnly}
                    className={"node-input"}
                />
            </SettingRow>
        </SettingsWrapper>
    );
};
