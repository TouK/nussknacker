import React, { useState } from "react";
import { Option } from "../FieldsSelect";
import StringSetting from "./StringSetting";
import InitialValue from "./InitialValue";
import { useTranslation } from "react-i18next";
import { FormControlLabel } from "@mui/material";
import { InputMode, PresetType, UpdatedItem, onChangeType } from "../item";
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
    localInputMode: Option[];
    selectedInputMode: InputMode;
    setSelectedInputMode: React.Dispatch<React.SetStateAction<InputMode>>;
}

export default function Settings(props: Settings) {
    const { item, path, onChange, variableTypes, currentOption } = props;
    const [presetType, setPresetType] = useState<PresetType>("Preset");
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
            <StringSetting {...props} presetType={presetType} setPresetType={setPresetType} />
            <ValidationsFields {...props} />
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
