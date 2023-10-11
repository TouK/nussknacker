import React from "react";
import { Option } from "../FieldsSelect";
import StringSetting from "./StringSetting";
import InitialValue from "./InitialValue";
import { useTranslation } from "react-i18next";
import { FormControlLabel } from "@mui/material";
import { UpdatedItem, onChangeType } from "../item";
import ValidationsFields from "./ValidationsFields";
import { VariableTypes } from "../../../../../types";
import { TextAreaNodeWithFocus } from "../../../../withFocus";
import { CustomSwitch, SettingLabelStyled, SettingRow, SettingsWrapper } from "./StyledSettingsComponnets";

interface Settings {
    item: UpdatedItem;
    path: string;
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    currentOption: Option;
}

export default function Settings({ item, path, onChange, variableTypes, currentOption }: Settings) {
    const { t } = useTranslation();

    return (
        <SettingsWrapper>
            <SettingRow>
                <SettingLabelStyled>{t("fragment.required", "Required:")}</SettingLabelStyled>
                <FormControlLabel
                    control={<CustomSwitch checked={item.required} onChange={() => onChange(`${path}.required`, !item.required)} />}
                    label=""
                />
            </SettingRow>
            <StringSetting path={path} item={item} currentOption={currentOption} onChange={onChange} />
            <ValidationsFields item={item} path={path} currentOption={currentOption} onChange={onChange} variableTypes={variableTypes} />
            <InitialValue item={item} path={path} currentOption={currentOption} onChange={onChange} variableTypes={variableTypes} />
            <SettingRow>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNodeWithFocus
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                />
            </SettingRow>
        </SettingsWrapper>
    );
}
