import React, { useState } from "react";
import InitialValue from "../fields/InitialValue";
import { SettingLabelStyled, SettingRow } from "../fields/StyledSettingsComponnets";
import { TextAreaNodeWithFocus } from "../../../../../../withFocus";
import { onChangeType, PresetType, UpdatedItem } from "../../../item";
import { useTranslation } from "react-i18next";
import PresetTypeGroup from "../fields/PresetTypeGroup";
import PresetTypesSetting from "../fields/PresetTypesSetting";
import ValidationsFields from "../fields/ValidationsFields";
import { VariableTypes } from "../../../../../../../types";

interface Props {
    item: UpdatedItem;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
}

export const AnyValueWithSuggestionVariant = ({ item, path, onChange, variableTypes }: Props) => {
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
                fixedValuesList={item.fixedValuesList}
                fixedValuesPresets={item.fixedValuesPresets}
                fixedValuesListPresetId={item.fixedValuesListPresetId}
            />
            <ValidationsFields path={path} onChange={onChange} item={item} variableTypes={variableTypes} />
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
                fixedValuesOptions={item.fixedValuesPresets || item.fixedValuesList}
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
