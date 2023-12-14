import React from "react";
import { t } from "i18next";
import { FormControlLabel } from "@mui/material";
import ValidationFields from "./ValidationFields";
import { onChangeType, AnyValueWithSuggestionsParameterVariant, AnyValueParameterVariant, DefaultParameterVariant } from "../../../item";
import { SettingRow, SettingLabelStyled, CustomSwitch } from "./StyledSettingsComponnets";
import { NodeValidationError, VariableTypes } from "../../../../../../../types";
import { Error } from "../../../../editors/Validators";

interface Validation {
    item: AnyValueWithSuggestionsParameterVariant | AnyValueParameterVariant | DefaultParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: NodeValidationError[];
}

export function ValidationsFields(props: ValidationsFields) {
    const { onChange, path, variableTypes, item, readOnly, errors } = props;
    const [validation, setValidation] = useState(true);

    return (
        <>
            <SettingRow>
                <SettingLabelStyled required>{t("fragment.validation.validation", "Validation:")}</SettingLabelStyled>
                <FormControlLabel
                    control={
                        <CustomSwitch
                            disabled={readOnly}
                            checked={validation}
                            onChange={(event) => onChange(`${path}.validationExpression.validation`, event.currentTarget.checked)}
                        />
                    }
                    label=""
                />
                <div style={{ width: "100%", justifyContent: "flex-end", display: "flex" }}>
                    <SettingLabelStyled style={{ flexBasis: "70%", minWidth: "70%" }}>
                        {t(
                            "fragment.validation.validationWarning",
                            "When validation is enabled, only literal values and operations on them are allowed in the parameter's value (i.e. the value passed to the fragment when it is used). This is because Nussknacker has to be able to evaluate it at deployment time.",
                        )}
                    </SettingLabelStyled>
                </div>
            </SettingRow>
            {validation && (
                <ValidationFields
                    path={path}
                    onChange={onChange}
                    failedMessage={item.validationExpression.failedMessage}
                    expression={item.validationExpression.expression}
                    variableTypes={variableTypes}
                    readOnly={readOnly}
                    errors={errors}
                />
            )}
        </>
    );
}
