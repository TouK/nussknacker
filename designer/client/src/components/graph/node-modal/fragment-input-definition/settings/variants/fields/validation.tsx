import React from "react";
import { t } from "i18next";
import { Box, FormControl, FormControlLabel, Typography } from "@mui/material";
import ValidationFields from "./ValidationFields";
import {
    onChangeType,
    AnyValueWithSuggestionsParameterVariant,
    AnyValueParameterVariant,
    DefaultParameterVariant,
    ValueCompileTimeValidation,
} from "../../../item";
import { SettingLabelStyled, CustomSwitch } from "./StyledSettingsComponnets";
import { NodeValidationError, VariableTypes } from "../../../../../../../types";
import { ExpressionLang } from "../../../../editors/expression/types";
import { useSettings } from "../../SettingsProvider";
import { nodeValue } from "../../../../NodeDetailsContent/NodeTableStyled";

interface Validation {
    item: AnyValueWithSuggestionsParameterVariant | AnyValueParameterVariant | DefaultParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: NodeValidationError[];
}

export function ValidationsFields(props: Validation) {
    const { onChange, path, variableTypes, item, readOnly, errors } = props;

    const validationEnabled = Boolean(item.valueCompileTimeValidation);
    const { temporaryValueCompileTimeValidation, handleTemporaryValueCompileTimeValidation } = useSettings();

    return (
        <>
            <FormControl>
                <SettingLabelStyled required>{t("fragment.validation.validation", "Validation:")}</SettingLabelStyled>
                <Box className={nodeValue}>
                    <FormControlLabel
                        control={
                            <CustomSwitch
                                disabled={readOnly}
                                checked={validationEnabled}
                                onChange={(event) => {
                                    if (event.currentTarget.checked) {
                                        const defaultValueCompileTimeValidation: ValueCompileTimeValidation = {
                                            validationExpression: {
                                                language: ExpressionLang.SpEL,
                                                expression: "true",
                                            },
                                            validationFailedMessage: null,
                                        };

                                        onChange(
                                            `${path}.valueCompileTimeValidation`,
                                            temporaryValueCompileTimeValidation || defaultValueCompileTimeValidation,
                                        );
                                    } else {
                                        handleTemporaryValueCompileTimeValidation(item.valueCompileTimeValidation);
                                        onChange(`${path}.valueCompileTimeValidation`, null);
                                    }
                                }}
                            />
                        }
                        label=""
                    />
                    <Typography component={"p"} color={"text.secondary"} variant={"caption"}>
                        {t(
                            "fragment.validation.validationWarning",
                            "When validation is enabled, only literal values and operations on them are allowed in the parameter's value (i.e. the value passed to the fragment when it is used). This is because Nussknacker has to be able to evaluate it at deployment time.",
                        )}
                    </Typography>
                </Box>
            </FormControl>
            {validationEnabled && (
                <ValidationFields
                    path={path}
                    onChange={onChange}
                    validationFailedMessage={item.valueCompileTimeValidation.validationFailedMessage}
                    validationExpression={item.valueCompileTimeValidation.validationExpression}
                    variableTypes={variableTypes}
                    readOnly={readOnly}
                    errors={errors}
                    name={item.name}
                    typ={item.typ}
                />
            )}
        </>
    );
}
