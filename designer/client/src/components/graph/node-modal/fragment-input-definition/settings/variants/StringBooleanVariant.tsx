import React from "react";
import { FormControlLabel } from "@mui/material";
import InputModeSelect from "./fields/InputModeSelect";
import { CustomSwitch, SettingLabelStyled, SettingRow, SettingsWrapper } from "./fields/StyledSettingsComponnets";
import { InputMode, onChangeType, StringOrBooleanParameterVariant } from "../../item";
import { VariableTypes } from "../../../../../../types";
import { useTranslation } from "react-i18next";
import { AnyValueVariant, AnyValueWithSuggestionVariant, FixedListVariant } from "./StringBooleanVariants";

interface Props {
    item: StringOrBooleanParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
}

export const StringBooleanVariant = ({ item, path, variableTypes, onChange }: Props) => {
    const inputModeOptions = [
        { label: "Fixed list", value: InputMode.FixedList },
        { label: "Any value with suggestions", value: InputMode.AnyValueWithSuggestions },
        { label: "Any value", value: InputMode.AnyValue },
    ];

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
            <InputModeSelect path={path} onChange={onChange} item={item} inputModeOptions={inputModeOptions} />
            {item.inputMode === InputMode.AnyValue && (
                <AnyValueVariant item={item} onChange={onChange} path={path} variableTypes={variableTypes} />
            )}
            {item.inputMode === InputMode.FixedList && <FixedListVariant item={item} onChange={onChange} path={path} />}
            {item.inputMode === InputMode.AnyValueWithSuggestions && (
                <AnyValueWithSuggestionVariant item={item} onChange={onChange} path={path} variableTypes={variableTypes} />
            )}
        </SettingsWrapper>
    );
};
