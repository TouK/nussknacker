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
}

export const FixedValueVariant = ({ item, path, onChange }: Props) => {
    const [presetType, setPresetType] = useState<PresetType>(PresetType.Preset);

    const { t } = useTranslation();

    const presetListItemOptions: Option[] = (item.fixedValuesPresets[item.fixedValuesListPresetId] ?? []).map(({ label }) => ({
        label: label,
        value: label,
    }));
    const fixedValuesListOptions: Option[] = (item.fixedValuesList ?? []).map(({ label }) => ({ label, value: label }));

    return (
        <>
            <PresetTypeGroup presetType={presetType} setPresetType={setPresetType} path={path} onChange={onChange} />
            <PresetTypesSetting
                path={path}
                onChange={onChange}
                presetType={presetType}
                presetSelection={item.presetSelection}
                fixedValuesList={item.fixedValuesList}
                fixedValuesPresets={item.fixedValuesPresets}
                fixedValuesListPresetId={item.fixedValuesListPresetId}
            />
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
                options={presetType === "UserDefinedList" ? fixedValuesListOptions : presetListItemOptions}
            />
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
