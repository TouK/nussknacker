import React from "react";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { useTranslation } from "react-i18next";
import { FixedValuesType, onChangeType, FixedValuesOption, FixedListParameterVariant, FragmentFieldsErrors } from "../../../item";
import { ListItems } from "./ListItems";
import { Option, TypeSelect } from "../../../TypeSelect";
import { FixedValuesPresets, ReturnedType, VariableTypes } from "../../../../../../../types";
import { UserDefinedListInput } from "./UserDefinedListInput";

interface FixedValuesSetting extends Pick<FixedListParameterVariant, "presetSelection"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesType: FixedValuesType;
    fixedValuesList: FixedValuesOption[];
    fixedValuesPresets: FixedValuesPresets;
    fixedValuesListPresetId: string;
    readOnly: boolean;
    variableTypes: VariableTypes;
    fieldsErrors: FragmentFieldsErrors;
    typ: ReturnedType;
}

export function FixedValuesSetting({
    path,
    fixedValuesType = FixedValuesType.UserDefinedList,
    onChange,
    fixedValuesListPresetId,
    fixedValuesPresets,
    fixedValuesList,
    readOnly,
    variableTypes,
    fieldsErrors,
    typ,
}: FixedValuesSetting) {
    const { t } = useTranslation();

    const presetListOptions: Option[] = Object.keys(fixedValuesPresets ?? {}).map((key) => ({ label: key, value: key }));

    const selectedPresetValueExpressions: Option[] = (fixedValuesPresets?.[fixedValuesListPresetId] ?? []).map(
        (selectedPresetValueExpression) => ({ label: selectedPresetValueExpression.label, value: selectedPresetValueExpression.label }),
    );

    return (
        <>
            {fixedValuesType === FixedValuesType.Preset && (
                <SettingRow>
                    <SettingLabelStyled required>{t("fragment.presetSelection", "Preset selection:")}</SettingLabelStyled>
                    <TypeSelect
                        readOnly={readOnly}
                        onChange={(value) => {
                            onChange(`${path}.fixedValuesListPresetId`, value);
                            onChange(`${path}.initialValue`, null);
                        }}
                        value={presetListOptions.find((presetListOption) => presetListOption.value === fixedValuesListPresetId)}
                        options={presetListOptions}
                    />
                    {selectedPresetValueExpressions?.length > 0 && (
                        <ListItems items={selectedPresetValueExpressions} errors={fieldsErrors.fixedValuesList} />
                    )}
                </SettingRow>
            )}
            {fixedValuesType === FixedValuesType.UserDefinedList && (
                <UserDefinedListInput
                    fixedValuesList={fixedValuesList}
                    variableTypes={variableTypes}
                    readOnly={readOnly}
                    onChange={onChange}
                    path={path}
                    errors={fieldsErrors.fixedValuesList}
                    typ={typ}
                />
            )}
        </>
    );
}
