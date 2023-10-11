import React from "react";
import { useTranslation } from "react-i18next";
import { SettingLabelStyled, SettingRow, SyledFormControlLabel } from "./StyledSettingsComponnets";
import { FormControlLabel, Radio, RadioGroup } from "@mui/material";
import { variables } from "../../../../../stylesheets/variables";
import { onChangeType, PresetType } from "../item";

interface PresetTypeGroup {
    onChange: (path: string, value: onChangeType) => void;
    presetType: PresetType;
    path: string;
}

export default function PresetTypeGroup({ presetType, onChange, path }) {
    const { t } = useTranslation();

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
                    sx={{ color: variables.defaultTextColor }}
                    value="Preset"
                    control={<Radio />}
                    label={<SyledFormControlLabel>{t("fragment.settings.preset", "Preset")}</SyledFormControlLabel>}
                />
                <FormControlLabel
                    sx={{ color: variables.defaultTextColor }}
                    value="UserDefinitionList"
                    control={<Radio />}
                    label={<SyledFormControlLabel>{t("fragment.settings.userDefinedList", "User defined list")}</SyledFormControlLabel>}
                />
            </RadioGroup>
        </SettingRow>
    );
}
