import React from "react";
import InitialValue from "../fields/InitialValue";
import { SettingLabelStyled, SettingRow } from "../fields/StyledSettingsComponnets";
import { TextAreaNodeWithFocus } from "../../../../../../withFocus";
import { AnyValueWithSuggestionsParameterVariant, onChangeType } from "../../../item";
import { useTranslation } from "react-i18next";
import { FixedValuesGroup } from "../fields/FixedValuesGroup";
import { FixedValuesSetting } from "../fields/FixedValuesSetting";
import ValidationsFields from "../fields/ValidationsFields";
import { FixedValuesPresets, VariableTypes } from "../../../../../../../types";
import { Option } from "../../../TypeSelect";

interface Props {
    item: AnyValueWithSuggestionsParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    fixedValuesPresets: FixedValuesPresets;
}

export const AnyValueWithSuggestionVariant = ({ item, path, onChange, variableTypes, fixedValuesPresets }: Props) => {
    const { t } = useTranslation();

    const presetListItemOptions: Option[] = (fixedValuesPresets?.[item.fixedValuesListPresetId] ?? []).map(({ label }) => ({
        label: label,
        value: label,
    }));
    const fixedValuesListOptions: Option[] = (item.fixedValuesList ?? []).map(({ label }) => ({ label, value: label }));

    const fixedValuesType = item.fixedValuesType;

    return (
        <>
            <FixedValuesGroup path={path} onChange={onChange} fixedValuesType={fixedValuesType} />
            <FixedValuesSetting
                path={path}
                onChange={onChange}
                fixedValuesType={fixedValuesType}
                presetSelection={item.presetSelection}
                fixedValuesList={item.fixedValuesList}
                fixedValuesPresets={fixedValuesPresets}
                fixedValuesListPresetId={item.fixedValuesListPresetId}
            />
            <ValidationsFields path={path} onChange={onChange} item={item} variableTypes={variableTypes} />
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
