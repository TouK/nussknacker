import React from "react";
import { useTranslation } from "react-i18next";
import { FixedListItemVariant, onChangeType } from "../../../item";
import InitialValue from "../fields/InitialValue";
import { FixedValuesGroup } from "../fields/FixedValuesGroup";
import { FixedValuesSetting } from "../fields/FixedValuesSetting";
import { SettingLabelStyled, SettingRow } from "../fields/StyledSettingsComponnets";
import { TextAreaNodeWithFocus } from "../../../../../../withFocus";
import { Option } from "../../../TypeSelect";

interface Props {
    item: FixedListItemVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
}

export const FixedListVariant = ({ item, path, onChange }: Props) => {
    const { t } = useTranslation();

    const presetListItemOptions: Option[] = (item.fixedValuesPresets?.[item.fixedValuesListPresetId] ?? []).map(({ label }) => ({
        label: label,
        value: label,
    }));
    const fixedValuesListOptions: Option[] = (item.fixedValuesList ?? []).map(({ label }) => ({ label, value: label }));
    const fixedValuesType = item.fixedValuesType;

    return (
        <>
            <FixedValuesGroup fixedValuesType={fixedValuesType} path={path} onChange={onChange} />
            <FixedValuesSetting
                path={path}
                onChange={onChange}
                fixedValuesType={fixedValuesType}
                presetSelection={item.presetSelection}
                fixedValuesList={item.fixedValuesList}
                fixedValuesPresets={item.fixedValuesPresets}
                fixedValuesListPresetId={item.fixedValuesListPresetId}
            />
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
                options={fixedValuesType === "UserDefinedList" ? fixedValuesListOptions : presetListItemOptions}
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
