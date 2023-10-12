import React from "react";
import { Option } from "../FieldsSelect";
import { TypeSelect } from "../TypeSelect";
import { useTranslation } from "react-i18next";
import { UpdatedItem, onChangeType } from "../item";
import PresetTypesSetting from "./PresetTypesSetting";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { isValidOption } from "../item/utils";
import PresetTypeGroup from "./PresetTypeGroup";

interface StringSetting {
    onChange: (path: string, value: onChangeType) => void;
    item: UpdatedItem;
    currentOption: Option;
    path: string;
}

export default function StringSetting({ onChange, path, item, currentOption }: StringSetting) {
    const { t } = useTranslation();
    const localInputMode = ["Fixed list", "Any value with suggestions", "Any value"];

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
                                onChange(`${path}.inputMode`, value);
                            }}
                            value={{ value: item.inputMode ?? localInputMode[0], label: item.inputMode ?? localInputMode[0] }}
                            options={localInputMode.map((option) => ({ value: option, label: option }))}
                        />
                    </SettingRow>
                    <PresetTypeGroup
                        path={path}
                        onChange={onChange}
                        allowOnlyValuesFromFixedValuesList={item.allowOnlyValuesFromFixedValuesList}
                    />
                    <PresetTypesSetting
                        path={path}
                        onChange={onChange}
                        allowOnlyValuesFromFixedValuesList={item.allowOnlyValuesFromFixedValuesList}
                        presetSelection={item.presetSelection}
                        addListItem={item.addListItem}
                    />
                </>
            )}
        </>
    );
}
