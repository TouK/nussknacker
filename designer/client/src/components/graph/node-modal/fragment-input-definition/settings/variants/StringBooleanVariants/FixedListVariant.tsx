import React from "react";
import { useTranslation } from "react-i18next";
import { FixedListParameterVariant, FixedValuesType, onChangeType } from "../../../item";
import InitialValue from "../fields/InitialValue";
import { FixedValuesGroup } from "../fields/FixedValuesGroup";
import { FixedValuesSetting } from "../fields/FixedValuesSetting";
import { SettingLabelStyled, SettingRow } from "../fields/StyledSettingsComponnets";
import { TextAreaNodeWithFocus } from "../../../../../../withFocus";
import { FixedValuesPresets, VariableTypes } from "../../../../../../../types";
import { Error } from "../../../../editors/Validators";

interface Props {
    item: FixedListParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesPresets: FixedValuesPresets;
    readOnly: boolean;
    variableTypes: VariableTypes;
    fieldsErrors: Error[];
}

export const FixedListVariant = ({ item, path, onChange, fixedValuesPresets, readOnly, variableTypes, fieldsErrors }: Props) => {
    const { t } = useTranslation();

    const presetListItemOptions = fixedValuesPresets?.[item.fixedValuesListPresetId] ?? [];

    const fixedValuesList = item.inputConfig.fixedValuesList ?? [];

    //TODO: Remove optional value when backend ready
    const fixedValuesType = item.fixedValuesType || FixedValuesType.UserDefinedList;

    return (
        <>
            {/*<FixedValuesGroup fixedValuesType={fixedValuesType} path={path} onChange={onChange} readOnly={readOnly} />*/}
            <FixedValuesSetting
                path={path}
                onChange={onChange}
                fixedValuesType={fixedValuesType}
                presetSelection={item.presetSelection}
                fixedValuesList={fixedValuesList}
                fixedValuesPresets={fixedValuesPresets}
                fixedValuesListPresetId={item.fixedValuesListPresetId}
                readOnly={readOnly}
                variableTypes={variableTypes}
                fieldsErrors={fieldsErrors}
                typ={item.typ}
                name={item.name}
            />
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
                options={fixedValuesType === "UserDefinedList" ? fixedValuesList : presetListItemOptions}
                readOnly={readOnly}
                variableTypes={variableTypes}
                fieldsErrors={fieldsErrors}
                fieldName={`$param.${item.name}.$initialValue`}
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
