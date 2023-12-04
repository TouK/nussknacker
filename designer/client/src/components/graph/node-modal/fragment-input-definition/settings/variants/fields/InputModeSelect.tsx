import React from "react";
import { Option } from "../../../FieldsSelect";
import { TypeSelect } from "../../../TypeSelect";
import { useTranslation } from "react-i18next";
import { InputMode, onChangeType, StringOrBooleanParameterVariant } from "../../../item";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { useSettings } from "../../SettingsProvider";

interface Props {
    onChange: (path: string, value: onChangeType) => void;
    item: StringOrBooleanParameterVariant;
    path: string;
    inputModeOptions: Option[];
    readOnly: boolean;
}

export default function InputModeSelect(props: Props) {
    const { onChange, path, item, inputModeOptions } = props;
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
            <SettingRow>
                <SettingLabelStyled required>{t("fragment.settings.inputMode", "Input mode:")}</SettingLabelStyled>
                <TypeSelect
                    readOnly={props.readOnly}
                    onChange={(value: InputMode) => {
                        const fixedValuesList =
                            item.valueEditor?.fixedValuesList?.length > 0 ? item.valueEditor.fixedValuesList : temporaryUserDefinedList;

                        onChange(`${path}.initialValue`, null);

                        if (value === InputMode.AnyValue) {
                            onChange(`${path}.valueEditor`, null);
                        } else if (value === InputMode.AnyValueWithSuggestions) {
                            onChange(`${path}.valueEditor.allowOtherValue`, true);
                            onChange(`${path}.valueEditor.fixedValuesList`, fixedValuesList);
                            onChange(`${path}.valueEditor.fixedValuesPresetId`, null);
                        } else {
                            onChange(`${path}.valueEditor.allowOtherValue`, false);
                            onChange(`${path}.valueEditor.fixedValuesList`, fixedValuesList);
                            onChange(`${path}.valueEditor.fixedValuesPresetId`, null);
                        }
                    }}
                    value={inputModeOptions.find((inputModeOption) => inputModeOption.value === value)}
                    options={inputModeOptions}
                />
            </SettingRow>
        </>
    );
}
