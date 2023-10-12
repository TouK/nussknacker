import React from "react";
import { useTranslation } from "react-i18next";
import { SettingLabelStyled, SettingRow, SyledFormControlLabel } from "./StyledSettingsComponnets";
import { FormControlLabel, Radio, RadioGroup, useTheme } from "@mui/material";
import { onChangeType } from "../item";

interface PresetTypeGroup {
    onChange: (path: string, value: onChangeType) => void;
    allowOnlyValuesFromFixedValuesList: boolean;
    path: string;
}

export default function PresetTypeGroup({ allowOnlyValuesFromFixedValuesList, onChange, path }: PresetTypeGroup) {
    const { t } = useTranslation();
    const theme = useTheme();

    return (
        <SettingRow>
            <SettingLabelStyled></SettingLabelStyled>
            <RadioGroup
                value={allowOnlyValuesFromFixedValuesList}
                onChange={(event) => {
                    onChange(`${path}.allowOnlyValuesFromFixedValuesList`, event.target.value === "true");
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
                    value="true"
                    control={<Radio />}
                    label={<SyledFormControlLabel>{t("fragment.settings.preset", "Preset")}</SyledFormControlLabel>}
                />
                <FormControlLabel
                    sx={{ color: theme.custom.colors.secondaryColor }}
                    value="false"
                    control={<Radio />}
                    label={<SyledFormControlLabel>{t("fragment.settings.userDefinedList", "User defined list")}</SyledFormControlLabel>}
                />
            </RadioGroup>
        </SettingRow>
    );
}
