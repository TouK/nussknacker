import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { onChangeType, PresetType, UpdatedItem } from "../../../item";
import InitialValue from "../fields/InitialValue";
import PresetTypeGroup from "../fields/PresetTypeGroup";
import PresetTypesSetting from "../fields/PresetTypesSetting";
import { SettingLabelStyled, SettingRow } from "../fields/StyledSettingsComponnets";
import { TextAreaNodeWithFocus } from "../../../../../../withFocus";
import { Option } from "../../../TypeSelect";

interface Props {
    item: UpdatedItem;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    presetListOptions: Option[];
}

export const FixedValueVariant = ({ item, path, onChange, presetListOptions }: Props) => {
    const [presetType, setPresetType] = useState<PresetType>("Preset");

    const { t } = useTranslation();

    return (
        <>
            <PresetTypeGroup presetType={presetType} setPresetType={setPresetType} path={path} onChange={onChange} />
            <PresetTypesSetting
                path={path}
                onChange={onChange}
                presetType={presetType}
                presetSelection={item.presetSelection}
                fixedValueList={item.fixedValueList}
                presetListOptions={presetListOptions}
            />
            <InitialValue path={path} item={item} onChange={onChange} options={presetListOptions || item.fixedValueList} />
            <SettingRow>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNodeWithFocus
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                />
            </SettingRow>
        </>
    );
};
