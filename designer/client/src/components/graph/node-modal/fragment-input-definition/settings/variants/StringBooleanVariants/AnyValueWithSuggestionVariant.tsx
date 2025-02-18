import React from "react";
import InitialValue from "../fields/InitialValue";
import { SettingLabelStyled } from "../fields/StyledSettingsComponnets";
import { TextAreaNode } from "../../../../../../FormElements";
import { AnyValueWithSuggestionsParameterVariant, onChangeType } from "../../../item";
import { useTranslation } from "react-i18next";
import { FixedValuesSetting } from "../fields/FixedValuesSetting";
import { NodeValidationError, VariableTypes } from "../../../../../../../types";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import { FormControl } from "@mui/material";
import { FixedValuesGroup } from "../fields/FixedValuesGroup";
import { nodeInput } from "../../../../NodeDetailsContent/NodeTableStyled";

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

    const fixedValuesList = item.valueEditor.fixedValuesList ?? [];

    const fixedValuesType = item.valueEditor.type;

    return (
        <>
            <FixedValuesGroup path={path} onChange={onChange} fixedValuesType={fixedValuesType} readOnly={readOnly} item={item} />
            <FixedValuesSetting
                path={path}
                onChange={onChange}
                fixedValuesType={fixedValuesType}
                presetSelection={item.presetSelection}
                fixedValuesList={fixedValuesList}
                dictId={item.valueEditor.dictId}
                readOnly={readOnly}
                variableTypes={variableTypes}
                errors={errors}
                typ={item.typ}
                name={item.name}
                initialValue={item.initialValue}
                userDefinedListInputLabel={t("fragment.userDefinedList.label.suggestedValues", "Suggested values:")}
            />
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
                readOnly={readOnly}
                options={fixedValuesList}
                variableTypes={variableTypes}
                fieldErrors={getValidationErrorsForField(errors, `$param.${item.name}.$initialValue`)}
            />
            <FormControl>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNode
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                    disabled={readOnly}
                    className={nodeInput}
                />
            </FormControl>
        </>
    );
};
