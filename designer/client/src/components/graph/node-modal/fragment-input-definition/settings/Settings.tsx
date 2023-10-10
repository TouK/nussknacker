import React from "react";
import { SettingLabelStyled, SettingRow, SettingsWrapper } from "./StyledSettingsComponnets";
import { Switch } from "@mui/material";
import { NodeInput } from "../../../../withFocus";
import { useTranslation } from "react-i18next";
import { Option } from "../FieldsSelect";
import { UpdatedItem, onChangeType } from "../item";
import ValidationsFields from "./ValidationsFields";
import StringSetting from "./StringSetting";
import InitialValue from "./InitialValue";

interface Settings {
    item: UpdatedItem;
    path: string;
    onChange: (path: string, value: onChangeType) => void;
    currentOption: Option;
}

export default function Settings({ item, path, onChange, currentOption }: Settings) {
    const { t } = useTranslation();

    return (
        <SettingsWrapper>
            <SettingRow>
                <SettingLabelStyled>{t("fragment.required", "Required:")}</SettingLabelStyled>
                <Switch />
            </SettingRow>
            <StringSetting path={path} item={item} currentOption={currentOption} onChange={onChange} />
            <ValidationsFields item={item} path={path} currentOption={currentOption} onChange={onChange} />
            <InitialValue item={item} path={path} onChange={onChange} />
            <SettingRow>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <NodeInput style={{ width: "70%" }} />
            </SettingRow>
        </SettingsWrapper>
    );
}
