import i18next from "i18next";
import { isEmpty } from "lodash";
import React from "react";

import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { FixedValuesEditor } from "./FixedValuesEditor";
import { EditorType, ExpressionLang } from "./types";

type Props = NonNullable<unknown>;

const SUPPORTED_LANGUAGE = ExpressionLang.SpEL;
const TRUE_EXPRESSION = "true";
const FALSE_EXPRESSION = "false";

const parseable = (expressionObj) => {
    const expression = expressionObj.expression;
    const language = expressionObj.language;
    return (expression === "true" || expression === "false") && language === SUPPORTED_LANGUAGE;
};

export const BoolEditor = prepareEditor(
    ({ expressionObj, readOnly, onValueChange, className, fieldErrors, showValidation = true }) => {
        const trueValue = { expression: TRUE_EXPRESSION, label: i18next.t("common.true", "true") };
        const falseValue = { expression: FALSE_EXPRESSION, label: i18next.t("common.false", "false") };

        return (
            <FixedValuesEditor
                editorConfig={{
                    type: EditorType.FIXED_VALUES_PARAMETER_EDITOR,
                    possibleValues: [trueValue, falseValue],
                }}
                expressionObj={expressionObj}
                onValueChange={onValueChange}
                readOnly={readOnly}
                className={className}
                fieldErrors={fieldErrors}
                showValidation={showValidation}
            />
        );
    },
    {
        isSwitchableTo: (expressionObj) => parseable(expressionObj) || isEmpty(expressionObj.expression),
        notSwitchableToHint: () =>
            i18next.t("editors.bool.notSwitchableToHint", "Expression must be equal to true or false to switch to {{displayName}} mode", {
                displayName: editorsParameters[EditorType.BOOL_PARAMETER_EDITOR].displayName,
            }),
    },
);
