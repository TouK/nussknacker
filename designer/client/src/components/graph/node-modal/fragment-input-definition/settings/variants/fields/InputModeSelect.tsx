import React from "react";
import { Option } from "../../../FieldsSelect";
import { TypeSelect } from "../../../TypeSelect";
import { useTranslation } from "react-i18next";
import { InputMode, onChangeType, StringOrBooleanItemVariant } from "../../../item";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";

interface Props {
    onChange: (path: string, value: onChangeType) => void;
    item: StringOrBooleanItemVariant;
    path: string;
    inputModeOptions: Option[];
}

export default function InputModeSelect(props: Props) {
    const { onChange, path, item, inputModeOptions } = props;
    const { t } = useTranslation();

    return (
        <>
            <SettingRow>
                <SettingLabelStyled>{t("fragment.settings.inputMode", "Input mode:")}</SettingLabelStyled>
                <TypeSelect
                    onChange={(value: InputMode) => {
                        if (value === "FixedList") {
                            onChange(`${path}.initialValue`, "");
                        }
                        onChange(`${path}.inputMode`, value);
                        onChange(`${path}.allowOnlyValuesFromFixedValuesList`, value === "FixedList");
                    }}
                    value={inputModeOptions.find((inputModeOption) => inputModeOption.value === item.inputMode)}
                    options={inputModeOptions}
                />
            </SettingRow>
        </>
    );
}
