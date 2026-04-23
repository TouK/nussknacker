import i18next from "i18next";
import { isEmpty } from "lodash";
import React from "react";

import type { NodeValidationError, VariableTypes } from "../../../../../../types/validation";
import { getAst } from "../../../aggregate/pareserHelpers";
import { RowFieldLabel } from "../../../aggregate/rowFieldLabel";
import { EditableEditor } from "../../EditableEditor";
import type { FieldError, Validator } from "../../Validators";
import { extendErrors, HandledErrorType, mandatoryValueValidator } from "../../Validators";
import { FormatterType, spelFormatters } from "../Formatter";
import { EditorType, ExpressionLang } from "../types";
import type { NameValueRecord } from "./nameValueRecordsListHelpers";

const emptyNameValidator: Validator = {
    isValid: (value) => value.length > 2,
    message: () => i18next.t("emptyNameValidator.message", "Required"),
    description: () => "",
    handledErrorType: HandledErrorType.EmptyMandatoryParameter,
};

const spelValuePreValidator: Validator = {
    isValid: (value) => isEmpty(value) || Boolean(getAst(value, false)),
    message: () => i18next.t("spelValuePreValidator.message", "This field have to contain valid SpEL expression"),
    description: () => i18next.t("validator.spelValue.description", "Please enter valid SpEL"),
    handledErrorType: HandledErrorType.InvalidSpelExpressionParameter,
};

type FieldProps = {
    fieldErrors: FieldError[];
    onChangeItem: (updated: Partial<NameValueRecord>) => void;
    showLabels: boolean;
    showValidation: boolean;
    showSwitch: boolean;
    variableTypes: VariableTypes;
    value: string;
    isValidating: boolean;
};

export const NameField = ({ showLabels, value, onChangeItem, fieldErrors, ...props }: FieldProps) => (
    <RowFieldLabel flexBasis="30%" label="name" showLabel={showLabels}>
        <EditableEditor
            editors={[{ type: EditorType.STATIC_STRING_PARAMETER_EDITOR }]}
            expressionObj={{ expression: value, language: ExpressionLang.SpEL }}
            onValueChange={(value) => {
                const expression = value.expression.trim();
                if (value.language === ExpressionLang.SpEL) {
                    return onChangeItem({ name: expression });
                }
                if (!expression) {
                    return onChangeItem({ name: "" });
                }
                return onChangeItem({ name: spelFormatters[FormatterType.String].encode(expression) });
            }}
            fieldErrors={extendErrors(fieldErrors as NodeValidationError[], value, null, [emptyNameValidator, spelValuePreValidator])}
            {...props}
        />
    </RowFieldLabel>
);

export const ValueField = ({ showLabels, value, onChangeItem, fieldErrors, ...props }: FieldProps) => (
    <RowFieldLabel flexBasis="70%" label="value" showLabel={showLabels}>
        <EditableEditor
            expressionObj={{ expression: value, language: ExpressionLang.SpEL }}
            onValueChange={({ expression }) => {
                onChangeItem({ value: expression });
            }}
            fieldErrors={extendErrors(fieldErrors as NodeValidationError[], value, null, [spelValuePreValidator, mandatoryValueValidator])}
            {...props}
        />
    </RowFieldLabel>
);
