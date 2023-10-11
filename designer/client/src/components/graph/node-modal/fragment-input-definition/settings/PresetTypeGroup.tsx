import React from "react";
import { useTranslation } from "react-i18next";
import { SettingLabelStyled, SettingRow, SyledFormControlLabel } from "./StyledSettingsComponnets";
import { FormControlLabel, Radio, RadioGroup, useTheme } from "@mui/material";
import { onChangeType, PresetType } from "../item";

interface PresetTypeGroup {
    onChange: (path: string, value: onChangeType) => void;
    presetType: PresetType;
    path: string;
}

export default function PresetTypeGroup({ presetType, onChange, path }) {
    const { t } = useTranslation();
    const theme = useTheme();

    return (
        <SettingRow>
            <SettingLabelStyled></SettingLabelStyled>
            <RadioGroup
                value={presetType}
                onChange={(event) => {
                    onChange(`${path}.presetType`, event.target.value);
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
                    value="Preset"
                    control={<Radio />}
                    label={<SyledFormControlLabel>{t("fragment.settings.preset", "Preset")}</SyledFormControlLabel>}
                />
                <FormControlLabel
                    sx={{ color: theme.custom.colors.secondaryColor }}
                    value="UserDefinitionList"
                    control={<Radio />}
                    label={<SyledFormControlLabel>{t("fragment.settings.userDefinedList", "User defined list")}</SyledFormControlLabel>}
                />
            </RadioGroup>
        </SettingRow>
    );
}
