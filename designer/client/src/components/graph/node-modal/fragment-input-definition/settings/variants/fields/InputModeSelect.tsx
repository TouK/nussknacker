import React from "react";
import { Option } from "../../../FieldsSelect";
import { TypeSelect } from "../../../TypeSelect";
import { useTranslation } from "react-i18next";
import { InputMode, onChangeType, StringOrBooleanParameterVariant } from "../../../item";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";

interface Props {
    onChange: (path: string, value: onChangeType) => void;
    item: StringOrBooleanParameterVariant;
    path: string;
    inputModeOptions: Option[];
}

export default function InputModeSelect(props: Props) {
    const { onChange, path, item, inputModeOptions } = props;
    const { t } = useTranslation();

    return (
        <>
            <SettingRow>
                <SettingLabelStyled required>{t("fragment.settings.inputMode", "Input mode:")}</SettingLabelStyled>
                <TypeSelect
                    onChange={(value: InputMode) => {
                        if (value === "FixedList") {
                            onChange(`${path}.initialValue`, "");
                        }
                        onChange(`${path}.inputMode`, value);
                    }}
                    value={inputModeOptions.find((inputModeOption) => inputModeOption.value === item.inputMode)}
                    options={inputModeOptions}
                />
            </SettingRow>
        </>
    );
}
