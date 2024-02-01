import React from "react";
import { SettingLabelStyled } from "./StyledSettingsComponnets";
import { useTranslation } from "react-i18next";
import { onChangeType, FragmentInputParameter, FixedValuesOption } from "../../../item";
import { Option, TypeSelect } from "../../../TypeSelect";
import { ExpressionLang } from "../../../../editors/expression/types";
import { EditableEditor } from "../../../../editors/EditableEditor";
import { VariableTypes } from "../../../../../../../types";
import { FieldError } from "../../../../editors/Validators";
import { EditorType } from "../../../../editors/expression/Editor";
import { FormControl } from "@mui/material";

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

    return (
        <FormControl>
            <SettingLabelStyled>{t("fragment.initialValue", "Initial value:")}</SettingLabelStyled>
            {options ? (
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
            ) : (
                <EditableEditor
                    expressionObj={{ language: ExpressionLang.SpEL, expression: item?.initialValue?.label }}
                    onValueChange={(value) => onChange(`${path}.initialValue`, { label: value, expression: value })}
                    variableTypes={variableTypes}
                    readOnly={readOnly}
                    param={{ editor: { type: EditorType.RAW_PARAMETER_EDITOR } }}
                    showValidation
                    fieldErrors={fieldErrors}
                />
            )}
        </FormControl>
    );
}
