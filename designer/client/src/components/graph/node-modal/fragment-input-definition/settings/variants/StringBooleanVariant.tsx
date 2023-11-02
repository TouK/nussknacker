import React, { useState } from "react";
import { FormControlLabel } from "@mui/material";
import InputModeSelect from "./fields/InputModeSelect";
import { CustomSwitch, SettingLabelStyled, SettingRow, SettingsWrapper } from "./fields/StyledSettingsComponnets";
import { InputMode, onChangeType, UpdatedItem } from "../../item";
import { Option } from "../../FieldsSelect";
import { VariableTypes } from "../../../../../../types";
import { useTranslation } from "react-i18next";
import { AnyValueVariant, AnyValueWithSuggestionVariant, FixedValueVariant } from "./StringBooleanVariants";

interface Props {
    item: UpdatedItem;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    currentOption: Option;
    variableTypes: VariableTypes;
}

export const StringBooleanVariant = ({ item, path, variableTypes, onChange }: Props) => {
    const inputModeOptions = [
        { label: "Fixed list", value: InputMode.FixedList },
        { label: "Any value with suggestions", value: InputMode.AnyValueWithSuggestions },
        { label: "Any value", value: InputMode.AnyValue },
    ];

    const [selectedInputMode, setSelectedInputMode] = useState<InputMode>(inputModeOptions[0].value);
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
            <InputModeSelect
                path={path}
                onChange={onChange}
                item={item}
                selectedInputMode={selectedInputMode}
                inputModeOptions={inputModeOptions}
                setSelectedInputMode={setSelectedInputMode}
            />
            {selectedInputMode === InputMode.AnyValue && (
                <AnyValueVariant item={item} onChange={onChange} path={path} variableTypes={variableTypes} />
            )}
            {selectedInputMode === InputMode.FixedList && <FixedValueVariant item={item} onChange={onChange} path={path} />}
            {selectedInputMode === InputMode.AnyValueWithSuggestions && (
                <AnyValueWithSuggestionVariant item={item} onChange={onChange} path={path} variableTypes={variableTypes} />
            )}
        </SettingsWrapper>
    );
};
