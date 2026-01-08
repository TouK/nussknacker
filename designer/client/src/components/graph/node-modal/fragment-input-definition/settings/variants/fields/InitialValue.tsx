import React from "react";
import { useTranslation } from "react-i18next";

import type { VariableTypes } from "../../../../../../../types/validation";
import { DictParameterEditor } from "../../../../editors/expression/DictParameterEditor/DictParameterEditor";
import { SpelEditor } from "../../../../editors/expression/SpelEditor";
import { EditorType, ExpressionLang } from "../../../../editors/expression/types";
import { FormControl } from "../../../../editors/FormControl";
import type { FieldError } from "../../../../editors/Validators";
import type { FixedValuesOption, FragmentInputParameter, onChangeType } from "../../../item/types";
import { FixedValuesType } from "../../../item/types";
import type { Option } from "../../../TypeSelect";
import { TypeSelect } from "../../../TypeSelect";
import { SettingLabelStyled } from "./StyledSettingsComponnets";

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
                    onValueChange={(value) =>
                        onChange(`${path}.initialValue`, { label: item.valueEditor.dictId, expression: value.expression })
                    }
                    editorConfig={{ type: EditorType.DICT_PARAMETER_EDITOR, dictId: item.valueEditor.dictId }}
                    readOnly={!item.valueEditor.dictId}
                />
            ) : (
                <SpelEditor
                    expressionObj={{ language: ExpressionLang.SpEL, expression: item?.initialValue?.label }}
                    onValueChange={({ expression }) =>
                        onChange(`${path}.initialValue`, expression ? { label: expression, expression } : null)
                    }
                    variableTypes={variableTypes}
                    readOnly={readOnly}
                    showValidation={validationEnabled}
                    fieldErrors={fieldErrors}
                    editorConfig={{ type: EditorType.SPEL_PARAMETER_EDITOR }}
                />
            )}
        </FormControl>
    );
}
