import React from "react";
import { SettingLabelStyled } from "./StyledSettingsComponnets";
import { useTranslation } from "react-i18next";
import { FixedValuesOption, FixedValuesType, FragmentInputParameter, onChangeType } from "../../../item";
import { Option, TypeSelect } from "../../../TypeSelect";
import { ExpressionLang } from "../../../../editors/expression/types";
import { VariableTypes } from "../../../../../../../types";
import { FieldError } from "../../../../editors/Validators";
import { FormControl } from "@mui/material";
import { DictParameterEditor } from "../../../../editors/expression/DictParameterEditor";
import { EditorType } from "../../../../editors/expression/Editor";
import { EditableEditor } from "../../../../editors/EditableEditor";

interface InitialValue {
    item: FragmentInputParameter;
    path: string;
    onChange: (path: string, value: onChangeType) => void;
    options?: FixedValuesOption[];
    readOnly: boolean;
    variableTypes: VariableTypes;
    fieldErrors: FieldError[];
}

export default function InitialValue({ onChange, item, path, options, readOnly, variableTypes, fieldErrors }: InitialValue) {
    const { t } = useTranslation();

    const emptyOption = { label: "", value: "" };
    const optionsToDisplay: Option[] = [emptyOption, ...(options ?? []).map(({ label }) => ({ label, value: label }))];

    const validationEnabled = Boolean(item.valueCompileTimeValidation);

    return (
        <FormControl>
            <SettingLabelStyled>{t("fragment.initialValue", "Initial value:")}</SettingLabelStyled>
            {item?.valueEditor?.type === FixedValuesType.ValueInputWithFixedValuesProvided ? (
                <TypeSelect
                    onChange={(value) => {
                        const selectedOption = options.find((option) => option.label === value);
                        onChange(`${path}.initialValue`, selectedOption);
                    }}
                    value={optionsToDisplay.find((option) => option.value === item?.initialValue?.label)}
                    options={optionsToDisplay}
                    readOnly={readOnly}
                    placeholder={""}
                    fieldErrors={fieldErrors}
                />
            ) : item?.valueEditor?.type === FixedValuesType.ValueInputWithDictEditor ? (
                <DictParameterEditor
                    key={item.valueEditor.dictId}
                    fieldErrors={fieldErrors}
                    showValidation={validationEnabled}
                    expressionObj={{ language: ExpressionLang.SpEL, expression: item?.initialValue?.expression }}
                    onValueChange={(value) => onChange(`${path}.initialValue`, { label: item.valueEditor.dictId, expression: value })}
                    param={{ editor: { dictId: item.valueEditor.dictId } }}
                    readOnly={!item.valueEditor.dictId}
                />
            ) : (
                <EditableEditor
                    expressionObj={{ language: ExpressionLang.SpEL, expression: item?.initialValue?.label }}
                    onValueChange={(value) => onChange(`${path}.initialValue`, { label: value, expression: value })}
                    variableTypes={variableTypes}
                    readOnly={readOnly}
                    param={{ editor: { type: EditorType.RAW_PARAMETER_EDITOR } }}
                    showValidation={validationEnabled}
                    fieldErrors={fieldErrors}
                />
            )}
        </FormControl>
    );
}
