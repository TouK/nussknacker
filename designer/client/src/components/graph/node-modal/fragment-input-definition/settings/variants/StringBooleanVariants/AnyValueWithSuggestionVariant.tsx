import React from "react";
import InitialValue from "../fields/InitialValue";
import { SettingLabelStyled, SettingRow } from "../fields/StyledSettingsComponnets";
import { TextAreaNodeWithFocus } from "../../../../../../withFocus";
import { AnyValueWithSuggestionsParameterVariant, FixedValuesType, onChangeType } from "../../../item";
import { useTranslation } from "react-i18next";
import { FixedValuesGroup } from "../fields/FixedValuesGroup";
import { FixedValuesSetting } from "../fields/FixedValuesSetting";
import { FixedValuesPresets, VariableTypes } from "../../../../../../../types";

interface Props {
    item: AnyValueWithSuggestionsParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    fixedValuesPresets: FixedValuesPresets;
    readOnly: boolean;
}

export const AnyValueWithSuggestionVariant = ({ item, path, onChange, variableTypes, fixedValuesPresets, readOnly }: Props) => {
    const { t } = useTranslation();

    const presetListItemOptions = fixedValuesPresets?.[item.fixedValuesListPresetId] ?? [];

    const fixedValuesList = item.inputConfig.fixedValuesList ?? [];

    const fixedValuesType = item.fixedValuesType || FixedValuesType.UserDefinedList;

    return (
        <>
            <FixedValuesGroup path={path} onChange={onChange} fixedValuesType={fixedValuesType} readOnly={readOnly} />
            <FixedValuesSetting
                path={path}
                onChange={onChange}
                fixedValuesType={fixedValuesType}
                presetSelection={item.presetSelection}
                fixedValuesList={fixedValuesList}
                fixedValuesPresets={fixedValuesPresets}
                fixedValuesListPresetId={item.fixedValuesListPresetId}
                readOnly={readOnly}
            />
            {/*<ValidationsFields path={path} onChange={onChange} item={item} variableTypes={variableTypes} />*/}
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
                options={fixedValuesType === "UserDefinedList" ? fixedValuesList : presetListItemOptions}
                readOnly={readOnly}
            />
            <SettingRow>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNodeWithFocus
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                    disabled={readOnly}
                    className={"node-input"}
                />
            </SettingRow>
        </>
    );
};
