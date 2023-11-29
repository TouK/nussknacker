import React from "react";
import InitialValue from "../fields/InitialValue";
import { SettingLabelStyled, SettingRow } from "../fields/StyledSettingsComponnets";
import { TextAreaNodeWithFocus } from "../../../../../../withFocus";
import { AnyValueParameterVariant, onChangeType } from "../../../item";
import { VariableTypes } from "../../../../../../../types";
import { useTranslation } from "react-i18next";
import { Error } from "../../../../editors/Validators";
import { ValidationsFields } from "../fields/validation";

interface Props {
    item: AnyValueParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
    fieldsErrors: Error[];
}

export const AnyValueVariant = ({ item, path, onChange, readOnly, variableTypes, fieldsErrors }: Props) => {
    const { t } = useTranslation();

    return (
        <>
            <ValidationsFields path={path} item={item} onChange={onChange} variableTypes={variableTypes} readOnly={readOnly} />
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
                readOnly={readOnly}
                variableTypes={variableTypes}
                fieldsErrors={fieldsErrors}
                fieldName={`$param.${item.name}.$initialValue`}
            />
            <SettingRow>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNodeWithFocus
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                    disabled={readOnly}
                    className={"node-input"}
                />
            </SettingRow>
        </>
    );
};
