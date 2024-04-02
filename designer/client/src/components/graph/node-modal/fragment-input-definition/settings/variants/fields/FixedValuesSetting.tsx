import React from "react";
import { SettingLabelStyled } from "./StyledSettingsComponnets";
import { useTranslation } from "react-i18next";
import { FixedValuesType, onChangeType, FixedValuesOption, FixedListParameterVariant } from "../../../item";
import { ListItems } from "./ListItems";
import { Option, TypeSelect } from "../../../TypeSelect";
import { NodeValidationError, ReturnedType, VariableTypes } from "../../../../../../../types";
import { UserDefinedListInput } from "./UserDefinedListInput";
import { FormControl } from "@mui/material";

interface FixedValuesSetting extends Pick<FixedListParameterVariant, "presetSelection"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesType: FixedValuesType;
    fixedValuesList: FixedValuesOption[];
    fixedValuesListPresetId: string;
    readOnly: boolean;
    variableTypes: VariableTypes;
    errors: NodeValidationError[];
    typ: ReturnedType;
    name: string;
    initialValue: FixedValuesOption;
    processDefinitionDicts: Option[];
}

export function FixedValuesSetting({
    path,
    fixedValuesType,
    onChange,
    fixedValuesListPresetId,
    fixedValuesList,
    readOnly,
    variableTypes,
    errors,
    typ,
    name,
    initialValue,
    processDefinitionDicts,
}: FixedValuesSetting) {
    const { t } = useTranslation();

    return (
        <>
            {fixedValuesType === FixedValuesType.ValueInputWithFixedValuesPreset && (
                <FormControl>
                    <SettingLabelStyled required>{t("fragment.presetSelection", "Preset selection:")}</SettingLabelStyled>
                    <TypeSelect
                        readOnly={readOnly}
                        onChange={(value) => {
                            onChange(`${path}.fixedValuesListPresetId`, value);
                            onChange(`${path}.initialValue`, null);
                        }}
                        value={
                            processDefinitionDicts.find((presetListOption) => presetListOption.value === fixedValuesListPresetId) ?? null
                        }
                        options={processDefinitionDicts}
                        fieldErrors={[]}
                    />
                </FormControl>
            )}
            {fixedValuesType === FixedValuesType.ValueInputWithFixedValuesProvided && (
                <UserDefinedListInput
                    fixedValuesList={fixedValuesList}
                    variableTypes={variableTypes}
                    readOnly={readOnly}
                    onChange={onChange}
                    path={path}
                    errors={errors}
                    typ={typ}
                    name={name}
                    initialValue={initialValue}
                />
            )}
        </>
    );
}
