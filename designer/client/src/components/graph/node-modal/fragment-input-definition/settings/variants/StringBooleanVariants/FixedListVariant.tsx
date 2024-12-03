import React from "react";
import { useTranslation } from "react-i18next";
import { FixedListParameterVariant, onChangeType } from "../../../item";
import InitialValue from "../fields/InitialValue";
import { FixedValuesSetting } from "../fields/FixedValuesSetting";
import { SettingLabelStyled } from "../fields/StyledSettingsComponnets";
import { TextAreaNode } from "../../../../../../FormElements";
import { NodeValidationError, VariableTypes } from "../../../../../../../types";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import { FormControl } from "@mui/material";
import { FixedValuesGroup } from "../fields/FixedValuesGroup";
import { nodeInput } from "../../../../NodeDetailsContent/NodeTableStyled";

interface Props {
    item: FixedListParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    readOnly: boolean;
    variableTypes: VariableTypes;
    errors: NodeValidationError[];
}

export const FixedListVariant = ({ item, path, onChange, readOnly, variableTypes, errors }: Props) => {
    const { t } = useTranslation();

    const fixedValuesList = item.valueEditor.fixedValuesList ?? [];

    const fixedValuesType = item.valueEditor.type;

    return (
        <>
            <FixedValuesGroup fixedValuesType={fixedValuesType} path={path} onChange={onChange} readOnly={readOnly} item={item} />
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
                userDefinedListInputLabel={t("fragment.userDefinedList.label.possibleValues", "Possible values:")}
            />
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
                options={fixedValuesList}
                readOnly={readOnly}
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
