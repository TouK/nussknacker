import React from "react";
import { FormControl, FormControlLabel } from "@mui/material";
import InputModeSelect from "./fields/InputModeSelect";
import { CustomSwitch, SettingLabelStyled, SettingsWrapper } from "./fields/StyledSettingsComponnets";
import {
    InputMode,
    isAnyValueParameter,
    isAnyValueWithSuggestionsParameter,
    isFixedListParameter,
    onChangeType,
    PermittedTypeParameterVariant,
} from "../../item";
import { NodeValidationError, VariableTypes } from "../../../../../../types";
import { useTranslation } from "react-i18next";
import { AnyValueVariant, AnyValueWithSuggestionVariant, FixedListVariant } from "./StringBooleanVariants";

interface Props {
    item: PermittedTypeParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: NodeValidationError[];
}

export const PermittedTypeVariant = ({ item, path, variableTypes, onChange, readOnly, errors, ...props }: Props) => {
    const inputModeOptions = [
        { label: "Fixed list", value: InputMode.FixedList },
        { label: "Any value with suggestions", value: InputMode.AnyValueWithSuggestions },
        { label: "Any value", value: InputMode.AnyValue },
    ];

    const { t } = useTranslation();

    return (
        <SettingsWrapper {...props}>
            <FormControl>
                <SettingLabelStyled required>{t("fragment.required", "Required:")}</SettingLabelStyled>
                <FormControlLabel
                    control={
                        <CustomSwitch
                            disabled={readOnly}
                            checked={item.required}
                            onChange={() => onChange(`${path}.required`, !item.required)}
                        />
                    }
                    label=""
                />
            </FormControl>
            <InputModeSelect
                path={path}
                onChange={onChange}
                item={item}
                inputModeOptions={inputModeOptions}
                readOnly={readOnly}
                errors={errors}
            />
            {isAnyValueParameter(item) && (
                <AnyValueVariant
                    item={item}
                    onChange={onChange}
                    path={path}
                    variableTypes={variableTypes}
                    readOnly={readOnly}
                    errors={errors}
                />
            )}
            {isFixedListParameter(item) && (
                <FixedListVariant
                    item={item}
                    onChange={onChange}
                    path={path}
                    readOnly={readOnly}
                    variableTypes={variableTypes}
                    errors={errors}
                />
            )}
            {isAnyValueWithSuggestionsParameter(item) && (
                <AnyValueWithSuggestionVariant
                    item={item}
                    onChange={onChange}
                    path={path}
                    variableTypes={variableTypes}
                    readOnly={readOnly}
                    errors={errors}
                />
            )}
        </SettingsWrapper>
    );
};
