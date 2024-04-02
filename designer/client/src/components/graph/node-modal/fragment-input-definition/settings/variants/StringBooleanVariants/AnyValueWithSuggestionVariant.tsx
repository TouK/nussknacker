import React, { useEffect, useState } from "react";
import InitialValue from "../fields/InitialValue";
import { SettingLabelStyled } from "../fields/StyledSettingsComponnets";
import { TextAreaNodeWithFocus } from "../../../../../../withFocus";
import { AnyValueWithSuggestionsParameterVariant, FixedValuesType, onChangeType } from "../../../item";
import { useTranslation } from "react-i18next";
import { FixedValuesSetting } from "../fields/FixedValuesSetting";
import { NodeValidationError, VariableTypes } from "../../../../../../../types";
import { ValidationsFields } from "../fields/validation";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import { FormControl } from "@mui/material";
import { FixedValuesGroup } from "../fields/FixedValuesGroup";
import { useGetAllDicts } from "./useGetAllDicts";

interface Props {
    item: AnyValueWithSuggestionsParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: NodeValidationError[];
}

export const AnyValueWithSuggestionVariant = ({ item, path, onChange, variableTypes, readOnly, errors }: Props) => {
    const { t } = useTranslation();
    const { processDefinitionDicts } = useGetAllDicts();

    const presetListItemOptions = processDefinitionDicts?.[item.fixedValuesListPresetId] ?? [];

    const fixedValuesList = item.valueEditor.fixedValuesList ?? [];

    const fixedValuesType = item.valueEditor.type;

    return (
        <>
            <FixedValuesGroup path={path} onChange={onChange} fixedValuesType={fixedValuesType} readOnly={readOnly} />
            <FixedValuesSetting
                path={path}
                onChange={onChange}
                fixedValuesType={fixedValuesType}
                presetSelection={item.presetSelection}
                fixedValuesList={fixedValuesList}
                fixedValuesListPresetId={item.fixedValuesListPresetId}
                readOnly={readOnly}
                variableTypes={variableTypes}
                errors={errors}
                typ={item.typ}
                name={item.name}
                initialValue={item.initialValue}
                processDefinitionDicts={processDefinitionDicts}
            />
            <ValidationsFields
                path={path}
                onChange={onChange}
                item={item}
                variableTypes={variableTypes}
                readOnly={readOnly}
                errors={errors}
            />
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
                options={fixedValuesType === FixedValuesType.ValueInputWithFixedValuesProvided ? fixedValuesList : undefined}
                readOnly={readOnly}
                variableTypes={variableTypes}
                fieldErrors={getValidationErrorsForField(errors, `$param.${item.name}.$initialValue`)}
            />
            <FormControl>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNodeWithFocus
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                    disabled={readOnly}
                    className={"node-input"}
                />
            </FormControl>
        </>
    );
};
