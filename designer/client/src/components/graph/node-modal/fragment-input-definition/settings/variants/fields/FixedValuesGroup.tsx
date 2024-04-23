import React from "react";
import { useTranslation } from "react-i18next";
import { SettingLabelStyled } from "./StyledSettingsComponnets";
import { FormControl, FormControlLabel, Radio, RadioGroup, Typography, useTheme } from "@mui/material";
import { AnyValueWithSuggestionsParameterVariant, FixedListParameterVariant, FixedValuesType, onChangeType } from "../../../item";

interface FixedValuesGroup {
    item: AnyValueWithSuggestionsParameterVariant | FixedListParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesType: FixedValuesType;
    readOnly: boolean;
}

export function FixedValuesGroup({ item, onChange, path, fixedValuesType, readOnly }: FixedValuesGroup) {
    const { t } = useTranslation();

    return (
        <FormControl>
            <SettingLabelStyled></SettingLabelStyled>
            <RadioGroup
                value={fixedValuesType}
                onChange={(event) => {
                    onChange(`${path}.initialValue`, null);
                    onChange(`${path}.valueEditor.type`, event.target.value);

                    if (event.target.value === FixedValuesType.ValueInputWithFixedValuesProvided) {
                        onChange(`${path}.valueEditor.fixedValuesList`, item?.valueEditor?.fixedValuesList || []);
                    } else {
                        onChange(`${path}.valueEditor.dictId`, item.valueEditor.dictId || "");
                    }
                }}
            >
                <FormControlLabel
                    value={FixedValuesType.ValueInputWithDictEditor}
                    control={<Radio />}
                    label={<Typography variant={"caption"}>{t("fragment.settings.preset", "Preset")}</Typography>}
                />
                <FormControlLabel
                    value={FixedValuesType.ValueInputWithFixedValuesProvided}
                    control={<Radio />}
                    label={<Typography variant={"caption"}>{t("fragment.settings.userDefinedList", "User defined list")}</Typography>}
                    disabled={readOnly}
                />
            </RadioGroup>
        </FormControl>
    );
}
