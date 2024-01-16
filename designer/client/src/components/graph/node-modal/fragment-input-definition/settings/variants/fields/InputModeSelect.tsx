import React from "react";
import { Option } from "../../../FieldsSelect";
import { TypeSelect } from "../../../TypeSelect";
import { useTranslation } from "react-i18next";
import { FixedValuesType, InputMode, onChangeType, StringOrBooleanParameterVariant } from "../../../item";
import { SettingLabelStyled } from "./StyledSettingsComponnets";
import { useSettings } from "../../SettingsProvider";
import { FixedValuesPresets, NodeValidationError } from "../../../../../../../types";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import { FormControl } from "@mui/material";

interface Props {
    onChange: (path: string, value: onChangeType) => void;
    item: StringOrBooleanParameterVariant;
    path: string;
    inputModeOptions: Option[];
    readOnly: boolean;
    fixedValuesPresets: FixedValuesPresets;
    errors: NodeValidationError[];
}

export default function InputModeSelect(props: Props) {
    const { onChange, path, item, inputModeOptions, fixedValuesPresets, errors } = props;
    const { t } = useTranslation();
    const { temporaryUserDefinedList } = useSettings();

    const value =
        item.valueEditor === null
            ? InputMode.AnyValue
            : item.valueEditor.allowOtherValue
            ? InputMode.AnyValueWithSuggestions
            : InputMode.FixedList;
    return (
        <>
            <FormControl>
                <SettingLabelStyled required>{t("fragment.settings.inputMode", "Input mode:")}</SettingLabelStyled>
                <TypeSelect
                    readOnly={props.readOnly}
                    onChange={(value: InputMode) => {
                        const fixedValuesList =
                            item.valueEditor?.fixedValuesList?.length > 0 ? item.valueEditor.fixedValuesList : temporaryUserDefinedList;

                        const setInitialValue = () => {
                            if (item?.valueEditor?.type === FixedValuesType.ValueInputWithFixedValuesProvided) {
                                return onChange(
                                    `${path}.initialValue`,
                                    fixedValuesList.find((fixedValuesList) => fixedValuesList.label === item.initialValue.label)
                                        ? item.initialValue
                                        : null,
                                );
                            }

                            if (item?.valueEditor?.type === FixedValuesType.ValueInputWithFixedValuesPreset) {
                                const presetListOptions: Option[] = Object.keys(fixedValuesPresets ?? {}).map((key) => ({
                                    label: key,
                                    value: key,
                                }));

                                return onChange(
                                    `${path}.initialValue`,
                                    presetListOptions.find((presetListOption) => presetListOption.label === item.initialValue.label)
                                        ? item.initialValue
                                        : null,
                                );
                            }

                            return onChange(`${path}.initialValue`, null);
                        };

                        if (value === InputMode.AnyValue) {
                            onChange(`${path}.valueEditor`, null);
                        } else if (value === InputMode.AnyValueWithSuggestions) {
                            onChange(`${path}.valueEditor.allowOtherValue`, true);
                            onChange(`${path}.valueEditor.fixedValuesList`, fixedValuesList);
                            onChange(`${path}.valueEditor.fixedValuesPresetId`, null);
                            onChange(`${path}.valueEditor.type`, FixedValuesType.ValueInputWithFixedValuesProvided);
                            setInitialValue();
                        } else {
                            onChange(`${path}.valueEditor.allowOtherValue`, false);
                            onChange(`${path}.valueEditor.fixedValuesList`, fixedValuesList);
                            onChange(`${path}.valueEditor.fixedValuesPresetId`, null);
                            onChange(`${path}.valueEditor.type`, FixedValuesType.ValueInputWithFixedValuesProvided);
                            setInitialValue();
                        }
                    }}
                    value={inputModeOptions.find((inputModeOption) => inputModeOption.value === value)}
                    options={inputModeOptions}
                    fieldErrors={getValidationErrorsForField(errors, `$param.${item.name}.$inputMode`)}
                />
            </FormControl>
        </>
    );
}
