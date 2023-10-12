import React from "react";
import { SettingLabelStyled, SettingRow, fieldLabel } from "./StyledSettingsComponnets";
import { useTranslation } from "react-i18next";
import { UpdatedItem, onChangeType } from "../item";
import { Option } from "../FieldsSelect";
import { TypeSelect } from "../TypeSelect";
import { isValidOption } from "../item/utils";
import EditableEditor from "../../editors/EditableEditor";
import { ExpressionLang } from "../../editors/expression/types";
import { VariableTypes } from "../../../../../types";

interface InitialValue {
    item: UpdatedItem;
    path: string;
    currentOption: Option;
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
}

export default function InitialValue({ onChange, item, path, variableTypes, currentOption }: InitialValue) {
    const { t } = useTranslation();

    return (
        <>
            {item.allowOnlyValuesFromFixedValuesList && item.inputMode === "Fixed list" && isValidOption(currentOption.value) ? (
                <SettingRow>
                    <SettingLabelStyled>{t("fragment.initialValue", "Initial value:")}</SettingLabelStyled>
                    <TypeSelect
                        onChange={(value) => onChange(`${path}.initialValue`, value)}
                        value={{ value: item.initialValue, label: item.initialValue }}
                        options={[{ value: "option", label: "option" }]}
                    />
                </SettingRow>
            ) : (
                <EditableEditor
                    fieldName="initialValue"
                    fieldLabel={t("fragment.initialValue", "Initial value:")}
                    renderFieldLabel={() => fieldLabel(t("fragment.initialValue", "Initial value:"))}
                    expressionObj={{ language: ExpressionLang.SpEL, expression: item.initialValue }}
                    onValueChange={(value) => onChange(`${path}.initialValue`, value)}
                    variableTypes={variableTypes}
                />
            )}
        </>
    );
}
