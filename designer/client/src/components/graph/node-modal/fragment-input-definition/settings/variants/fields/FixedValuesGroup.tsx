import React from "react";
import { useTranslation } from "react-i18next";
import { SettingLabelStyled, StyledFormControlLabel } from "./StyledSettingsComponnets";
import { FormControl, FormControlLabel, Radio, RadioGroup } from "@mui/material";
import { FixedValuesType, onChangeType } from "../../../item";

interface FixedValuesGroup {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesType: FixedValuesType;
    readOnly: boolean;
}

export function FixedValuesGroup({ onChange, path, fixedValuesType, readOnly }: FixedValuesGroup) {
    const { t } = useTranslation();

    return (
        <FormControl>
            <SettingLabelStyled></SettingLabelStyled>
            <RadioGroup
                value={fixedValuesType}
                onChange={(event) => {
                    onChange(`${path}.initialValue`, null);
                    onChange(`${path}.valueEditor.type`, event.target.value);
                }}
            >
                <FormControlLabel
                    value={FixedValuesType.ValueInputWithFixedValuesPreset}
                    control={<Radio />}
                    label={<StyledFormControlLabel>{t("fragment.settings.preset", "Preset")}</StyledFormControlLabel>}
                />
                <FormControlLabel
                    value={FixedValuesType.ValueInputWithFixedValuesProvided}
                    control={<Radio />}
                    label={<StyledFormControlLabel>{t("fragment.settings.userDefinedList", "User defined list")}</StyledFormControlLabel>}
                    disabled={readOnly}
                />
            </RadioGroup>
        </FormControl>
    );
}
