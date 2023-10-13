import React from "react";
import { Option } from "../FieldsSelect";
import { TypeSelect } from "../TypeSelect";
import { useTranslation } from "react-i18next";
import { InputMode, PresetType, UpdatedItem, onChangeType } from "../item";
import PresetTypesSetting from "./PresetTypesSetting";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { isValidOption } from "../item/utils";
import PresetTypeGroup from "./PresetTypeGroup";

interface StringSetting {
    onChange: (path: string, value: onChangeType) => void;
    item: UpdatedItem;
    currentOption: Option;
    path: string;
    presetType: PresetType;
    setPresetType: React.Dispatch<React.SetStateAction<PresetType>>;
    setSelectedInputMode: React.Dispatch<React.SetStateAction<InputMode>>;
    selectedInputMode: InputMode;
    localInputMode: Option[];
}

export default function StringSetting(props: StringSetting) {
    const { onChange, path, item, currentOption, presetType, setPresetType, setSelectedInputMode, selectedInputMode, localInputMode } =
        props;
    const { t } = useTranslation();

    return (
        <>
            {isValidOption(currentOption.value) && (
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
                            value={{ value: selectedInputMode, label: selectedInputMode }}
                            options={localInputMode}
                        />
                    </SettingRow>
                    <PresetTypeGroup presetType={presetType} setPresetType={setPresetType} path={path} onChange={onChange} />
                    <PresetTypesSetting
                        path={path}
                        onChange={onChange}
                        presetType={presetType}
                        presetSelection={item.presetSelection}
                        fixedValueList={item.fixedValueList}
                    />
                </>
            )}
        </>
    );
}
