import React from "react";
import { useTranslation } from "react-i18next";
import { FixedListParameterVariant, FixedValuesType, onChangeType } from "../../../item";
import InitialValue from "../fields/InitialValue";
import { FixedValuesSetting } from "../fields/FixedValuesSetting";
import { SettingLabelStyled } from "../fields/StyledSettingsComponnets";
import { TextAreaNodeWithFocus } from "../../../../../../withFocus";
import { FixedValuesPresets, NodeValidationError, VariableTypes } from "../../../../../../../types";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import { FormControl } from "@mui/material";

interface Props {
    item: FixedListParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesPresets: FixedValuesPresets;
    readOnly: boolean;
    variableTypes: VariableTypes;
    errors: NodeValidationError[];
}

export const FixedListVariant = ({ item, path, onChange, fixedValuesPresets, readOnly, variableTypes, errors }: Props) => {
    const { t } = useTranslation();

    const presetListItemOptions = fixedValuesPresets?.[item.fixedValuesListPresetId] ?? [];

    const fixedValuesList = item.valueEditor.fixedValuesList ?? [];

    const fixedValuesType = item.valueEditor.type;

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
                errors={errors}
                typ={item.typ}
                name={item.name}
                initialValue={item.initialValue}
            />
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
                options={fixedValuesType === FixedValuesType.ValueInputWithFixedValuesProvided ? fixedValuesList : presetListItemOptions}
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
