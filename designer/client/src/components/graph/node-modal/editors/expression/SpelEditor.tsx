import { Box, type TooltipProps } from "@mui/material";
import type { ForwardedRef, ReactNode } from "react";
import React, { forwardRef, useCallback, useMemo } from "react";
import type ReactAce from "react-ace/lib/ace";
import { useTranslation } from "react-i18next";

import { tryParseOrNull } from "../../../../../common/JsonUtils";
import type { VariableTypes } from "../../../../../types/validation";
import { InfoTooltip } from "../InfoTooltip/InfoTooltip";
import type { FieldError } from "../Validators";
import type { OnValueChange } from "./Editor";
import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { ExpressionSuggestProps } from "./ExpressionSuggest";
import { ExpressionSuggest } from "./ExpressionSuggest";
import { ResetToDefaultButton } from "./ResetToDefaultButton";
import { addQuotesToExpression } from "./SpelQuotesUtils";
import type { ExpressionObj } from "./types";
import { EditorMode, EditorType, ExpressionLang } from "./types";

export type SpelEditorProps = {
    fieldErrors: FieldError[];
    isMarked?: boolean;
    showValidation?: boolean;
    readOnly?: boolean;
    onValueChange: OnValueChange;
    rows?: number;
    cols?: number;
    className?: string;
    variableTypes: VariableTypes;
    validationLabelInfo?: ReactNode;
    editorMode?: EditorMode;
    placeholder?: string;
    language?: ExpressionLang;
    infoText?: string;
    defaultValue?: ExpressionObj;
};

const infoTooltipCustomComponentsProps: TooltipProps["componentsProps"] = {
    tooltip: { sx: { maxWidth: "none" } },
};

const isParseable = (expressionObj: ExpressionObj) =>
    tryParseOrNull(expressionObj.expression) && typeof tryParseOrNull(expressionObj.expression) === "object";

function looksLikeSpelTemplateExpression(expr: string): boolean {
    const trimmed = expr.trim();
    return /#\{[\s\S]*?\}/.test(trimmed); // #{ ... }
}

export const SpelEditor = prepareEditor<SpelEditorProps>(
    forwardRef(function SpelEditorComponent(props, forwardedRef: ForwardedRef<ReactAce>) {
        const { t } = useTranslation();
        const {
            expressionObj,
            fieldErrors,
            isMarked,
            showValidation,
            readOnly,
            onValueChange,
            rows = 1,
            cols = 50,
            className,
            variableTypes,
            validationLabelInfo,
            editorMode,
            placeholder,
            language = editorsParameters[EditorType.SPEL_PARAMETER_EDITOR].language,
            defaultValue,
        } = props;

        const handleChange = useCallback(
            (expression: string) => {
                onValueChange({ expression, language });
            },
            [language, onValueChange],
        );

        const value = useMemo(() => expressionObj.expression, [expressionObj.expression]);

        const inputProps = useMemo<ExpressionSuggestProps["inputProps"]>(() => {
            const properties: ExpressionSuggestProps["inputProps"] = {
                rows,
                cols,
                value,
                language,
                onValueChange: handleChange,
                readOnly,
                ref: forwardedRef,
                editorMode: editorMode,
            };

            if (expressionObj.language === ExpressionLang.SpEL && !readOnly) {
                properties.placeholder = placeholder || t("editors.spelEditor.placeholder", "e.g. #input.someField");
                properties.InputAdornmentEnd = (
                    <InfoTooltip
                        customComponentsProps={infoTooltipCustomComponentsProps}
                        title={t(
                            "editors.spelEditor.infoText",
                            `You are using an expression-based input, allowing calculations and conditions. Access variables and helpers with \`#\`, e.g., \`#input.someField == 'value'\` or \`#UTIL.split('foo-bar', '-')\`. \n 
When accessing variables that support dynamic fields you can use \`#input['dynamicField'].toTargetType\`, e.g. \`#input['accountNo'].toLong\`. \n
Strings need to be quoted; use \`+\` to concatenate multiple strings values. \n
Use autocompletion to explore available options. To read more see [Documentation](https://nussknacker.io/documentation/docs/scenarios_authoring/Spel).`,
                        )}
                    />
                );
            }

            if (editorMode === EditorMode.SpELTemplate && !readOnly) {
                properties.placeholder = placeholder || t("editors.spelTemplateEditor.placeholder", "e.g. Hello #{ #input.someField }");
                properties.InputAdornmentEnd = (
                    <InfoTooltip
                        customComponentsProps={infoTooltipCustomComponentsProps}
                        title={t(
                            "editors.spelTemplateEditor.infoText",
                            `You are using a string-template-based input, allowing text with embedded expressions. Text should not be quoted. \n 
Embed expression with \`#{ }\`, e.g., \`Hello #{ #input.name }\`.  \n
When accessing variables that support dynamic fields you can use \`#input['dynamicField'].toTargetType\`, e.g. \`#input['accountNo'].toLong\`. \n
Use autocompletion to explore available options. To read more see [Documentation](https://nussknacker.io/documentation/docs/scenarios_authoring/Spel)`,
                        )}
                    />
                );
            }

            if (editorMode === EditorMode.JsonTemplate && !readOnly) {
                const defaultValueIsDifferentThanCurrentValue = defaultValue?.expression !== props.expressionObj.expression;
                const showResetToDefaultButton = defaultValue && defaultValueIsDifferentThanCurrentValue;

                properties.placeholder = placeholder || t("editors.jsonTemplateEditor.placeholder", 'e.g. { "key": "#{ #input.value }" }');
                properties.InputAdornmentEnd = (
                    <Box display={"flex"} flexDirection={"column"} alignItems={"center"} gap={0.5} width={"1rem"}>
                        <InfoTooltip
                            customComponentsProps={infoTooltipCustomComponentsProps}
                            title={t(
                                "editors.jsonTemplateEditor.infoText",
                                `You are using a json-template-based input, allowing json with embedded expressions. This input behave similar to string-template, with differences:\n
* It produces a data record, which is very useful when you produces some message and want to be sure that it will be in valid format\n
* It supports validations against schema, when used in schema-aware context\n
\n
Embed expression with \`#{ }\`, e.g., \n
\`\`\`jsonTemplate
{
  "name": "#{ #input.name }",
  "age": #{ #input.age },
  #{ #input.secondName ?: ', "secondName": "' + #input.secondName + '"' }
}
\`\`\`\n
In placeholders, you can use more complex types such as records and lists. To make sure that they will be rendered correctly, use \`#CONV.toJsonString(#complexType)\` helper function.\n
\n
When accessing variables that support dynamic fields you can use \`#input['dynamicField'].toTargetType\`, e.g. \`#input['accountNo'].toLong\`.\n
\n
Use autocompletion to explore available options. To read more see [Documentation](https://nussknacker.io/documentation/docs/scenarios_authoring/Spel)`,
                            )}
                        />
                        {showResetToDefaultButton && <ResetToDefaultButton defaultValue={defaultValue} handleChange={handleChange} />}
                    </Box>
                );
            }
            if (editorMode === EditorMode.JsonTemplate) {
                properties.useAceWorker = false;
            }

            return properties;
        }, [
            rows,
            cols,
            value,
            language,
            handleChange,
            readOnly,
            forwardedRef,
            editorMode,
            expressionObj.language,
            placeholder,
            t,
            defaultValue,
            props.expressionObj.expression,
        ]);

        return (
            <ExpressionSuggest
                className={className}
                inputProps={inputProps}
                variableTypes={variableTypes}
                fieldErrors={fieldErrors}
                isMarked={isMarked}
                showValidation={showValidation}
                validationLabelInfo={validationLabelInfo}
            />
        );
    }),
    {
        isSwitchableTo: (expressionObj) => {
            if (expressionObj.language === ExpressionLang.SpELTemplate) {
                return !looksLikeSpelTemplateExpression(expressionObj.expression);
            }

            return true;
        },
        notSwitchableToHint: () =>
            "The string-template-based input must be a literal value without embedded expressions to switch to expression mode",
        parseValueOnEditorChange: ({ expression, language }: ExpressionObj, newLanguage) => {
            if (language === ExpressionLang.DictKeyWithLabel) {
                return {
                    language: newLanguage,
                    expression: isParseable({ expression, language }) ? "" : expression,
                };
            }

            if (language === ExpressionLang.SpELTemplate) {
                return { expression: addQuotesToExpression({ expression, language: newLanguage }), language: newLanguage };
            }

            return { expression, language: newLanguage };
        },
    },
);
