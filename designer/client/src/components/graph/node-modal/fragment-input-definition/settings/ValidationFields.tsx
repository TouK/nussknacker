import React from "react";
import { useTranslation } from "react-i18next";
import { NodeInput } from "../../../../withFocus";
import { FragmentValidation, onChangeType } from "../item";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";

interface ValidationFields extends Omit<FragmentValidation, "validation"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
}

export default function ValidationFields({ validatioErrorMessage, validationExpression, path, onChange }: ValidationFields) {
    const { t } = useTranslation();

    return (
        <>
            <SettingRow>
                <SettingLabelStyled>{t("fragment.validation.validationExpression", "Validation expression:")}</SettingLabelStyled>
                <NodeInput
                    value={validationExpression}
                    onChange={(e) => onChange(`${path}.validationExpression`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                />
            </SettingRow>
            <SettingRow>
                <SettingLabelStyled>{t("fragment.validation.validationErrorMessage", "Validation error message:")}</SettingLabelStyled>
                <NodeInput
                    value={validatioErrorMessage}
                    onChange={(e) => onChange(`${path}.validatioErrorMessage`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                />
            </SettingRow>
        </>
    );
}
