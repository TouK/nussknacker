import React from "react";
import { useTranslation } from "react-i18next";
import { SettingLabelStyled, SettingRow, SyledFormControlLabel } from "./StyledSettingsComponnets";
import { FormControlLabel, Radio, RadioGroup, useTheme } from "@mui/material";
import { PresetType, onChangeType } from "../item";

interface PresetTypeGroup {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    presetType: PresetType;
    setPresetType: React.Dispatch<React.SetStateAction<PresetType>>;
}

export default function PresetTypeGroup(props: PresetTypeGroup) {
    const { onChange, path, presetType, setPresetType } = props;
    const { t } = useTranslation();
    const theme = useTheme();

    return (
        <SettingRow>
            <SettingLabelStyled></SettingLabelStyled>
            <RadioGroup
                value={presetType}
                onChange={(event) => {
                    setPresetType(event.target.value as PresetType);
                    if (event.target.value !== "Preset") {
                        onChange(`${path}.addListItem`, []);
                        onChange(`${path}.initialValue`, "");
                    } else {
                        onChange(`${path}.presetSelection`, []);
                    }
                }}
            >
                <FormControlLabel
                    sx={{ color: theme.custom.colors.secondaryColor }}
                    value={"Preset"}
                    control={<Radio />}
                    label={<SyledFormControlLabel>{t("fragment.settings.preset", "Preset")}</SyledFormControlLabel>}
                />
                <FormControlLabel
                    sx={{ color: theme.custom.colors.secondaryColor }}
                    value={"User defined list"}
                    control={<Radio />}
                    label={<SyledFormControlLabel>{t("fragment.settings.userDefinedList", "User defined list")}</SyledFormControlLabel>}
                />
            </RadioGroup>
        </SettingRow>
    );
}
