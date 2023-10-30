import React from "react";
import { Option } from "../../../FieldsSelect";
import { TypeSelect } from "../../../TypeSelect";
import { useTranslation } from "react-i18next";
import { InputMode, UpdatedItem, onChangeType } from "../../../item";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";

interface Props {
    onChange: (path: string, value: onChangeType) => void;
    item: UpdatedItem;
    path: string;
    setSelectedInputMode: React.Dispatch<React.SetStateAction<InputMode>>;
    selectedInputMode: InputMode;
    inputModeOptions: Option[];
}

export default function InputModeSelect(props: Props) {
    const { onChange, path, setSelectedInputMode, selectedInputMode, inputModeOptions } = props;
    const { t } = useTranslation();

    return (
        <>
            <SettingRow>
                <SettingLabelStyled>{t("fragment.settings.inputMode", "Input mode:")}</SettingLabelStyled>
                <TypeSelect
                    onChange={(value) => {
                        if (value === "Fixed list") {
                            onChange(`${path}.initialValue`, "");
                        }
                        setSelectedInputMode(value as InputMode);
                        onChange(`${path}.allowOnlyValuesFromFixedValuesList`, value === "Fixed list");
                    }}
                    value={inputModeOptions.find((inputModeOption) => inputModeOption.value === selectedInputMode)}
                    options={inputModeOptions}
                />
            </SettingRow>
        </>
    );
}
