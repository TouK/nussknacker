import React from "react";
import { useTranslation } from "react-i18next";
import { SettingLabelStyled, SettingRow, SyledFormControlLabel } from "./StyledSettingsComponnets";
import { FormControlLabel, Radio, RadioGroup, useTheme } from "@mui/material";
import { FixedValuesType, onChangeType } from "../../../item";

interface FixedValuesGroup {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesType: FixedValuesType;
}

export function FixedValuesGroup(props: FixedValuesGroup) {
    const { onChange, path, fixedValuesType } = props;
    const { t } = useTranslation();
    const theme = useTheme();

    return (
        <SettingRow>
            <SettingLabelStyled></SettingLabelStyled>
            <RadioGroup
                value={fixedValuesType}
                onChange={(event) => {
                    onChange(`${path}.initialValue`, "");
                    onChange(`${path}.fixedValuesType`, event.target.value);
                }}
            >
                <FormControlLabel
                    sx={{ color: theme.custom.colors.secondaryColor }}
                    value={FixedValuesType.Preset}
                    control={<Radio />}
                    label={<SyledFormControlLabel>{t("fragment.settings.preset", "Preset")}</SyledFormControlLabel>}
                />
                <FormControlLabel
                    sx={{ color: theme.custom.colors.secondaryColor }}
                    value={FixedValuesType.UserDefinedList}
                    control={<Radio />}
                    label={<SyledFormControlLabel>{t("fragment.settings.userDefinedList", "User defined list")}</SyledFormControlLabel>}
                />
            </RadioGroup>
        </SettingRow>
    );
}
