import React from "react";
import { FormControlLabel } from "@mui/material";
import InputModeSelect from "./fields/InputModeSelect";
import { CustomSwitch, SettingLabelStyled, SettingRow, SettingsWrapper } from "./fields/StyledSettingsComponnets";
import {
    InputMode,
    isAnyValueParameter,
    isAnyValueWithSuggestionsParameter,
    isFixedListParameter,
    onChangeType,
    StringOrBooleanParameterVariant,
} from "../../item";
import { FixedValuesPresets, VariableTypes } from "../../../../../../types";
import { useTranslation } from "react-i18next";
import { AnyValueVariant, AnyValueWithSuggestionVariant, FixedListVariant } from "./StringBooleanVariants";

interface Props {
    item: StringOrBooleanParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    fixedValuesPresets: FixedValuesPresets;
}

export const StringBooleanVariant = ({ item, path, variableTypes, onChange, fixedValuesPresets }: Props) => {
    const inputModeOptions = [
        { label: "Fixed list", value: InputMode.FixedList },
        { label: "Any value with suggestions", value: InputMode.AnyValueWithSuggestions },
        { label: "Any value", value: InputMode.AnyValue },
    ];

    const { t } = useTranslation();

    return (
        <SettingsWrapper>
            <SettingRow>
                <SettingLabelStyled required>{t("fragment.required", "Required:")}</SettingLabelStyled>
                <FormControlLabel
                    control={<CustomSwitch checked={item.required} onChange={() => onChange(`${path}.required`, !item.required)} />}
                    label=""
                />
            </SettingRow>
            <InputModeSelect path={path} onChange={onChange} item={item} inputModeOptions={inputModeOptions} />
            {isAnyValueParameter(item) && <AnyValueVariant item={item} onChange={onChange} path={path} variableTypes={variableTypes} />}
            {isFixedListParameter(item) && (
                <FixedListVariant item={item} onChange={onChange} path={path} fixedValuesPresets={fixedValuesPresets} />
            )}
            {isAnyValueWithSuggestionsParameter(item) && (
                <AnyValueWithSuggestionVariant
                    item={item}
                    onChange={onChange}
                    path={path}
                    variableTypes={variableTypes}
                    fixedValuesPresets={fixedValuesPresets}
                />
            )}
        </SettingsWrapper>
    );
};
