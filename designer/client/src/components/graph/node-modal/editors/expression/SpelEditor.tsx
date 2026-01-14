import type { ReactNode } from "react";
import React, { forwardRef, useCallback, useMemo } from "react";
import type ReactAce from "react-ace/lib/ace";

import { tryParseOrNull } from "../../../../../common/JsonUtils";
import type { VariableTypes } from "../../../../../types/validation";
import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { ExpressionSuggest } from "./ExpressionSuggest";
import { usePlaceholder } from "./helpText";
import { Info } from "./InputAdornmentEndColumn";
import { addQuotesToExpression } from "./SpelQuotesUtils";
import type { ExpressionObj } from "./types";
import { EditorMode, EditorType, ExpressionLang } from "./types";

export type SpelEditorProps = {
    isMarked?: boolean;
    rows?: number;
    cols?: number;
    variableTypes: VariableTypes;
    validationLabelInfo?: ReactNode;
    inputAdornmentEnd?: ReactNode;
    editorMode?: EditorMode;
    placeholder?: string;
    language?: ExpressionLang;
    infoText?: string;
    defaultValue?: ExpressionObj;
};

const isParseable = (expressionObj: ExpressionObj) =>
    tryParseOrNull(expressionObj.expression) && typeof tryParseOrNull(expressionObj.expression) === "object";

function looksLikeSpelTemplateExpression(expr: string): boolean {
    const trimmed = expr.trim();
    return /#\{[\s\S]*?\}/.test(trimmed); // #{ ... }
}

export const SpelEditor = prepareEditor<SpelEditorProps, ReactAce>(
    forwardRef(function SpelEditorComponent(
        {
            inputAdornmentEnd: InputAdornmentEnd,
            className,
            cols = 50,
            defaultValue,
            editorConfig,
            editorMode,
            expressionObj,
            fieldErrors,
            isMarked,
            language = editorsParameters[EditorType.SPEL_PARAMETER_EDITOR].language,
            onValueChange,
            placeholder,
            readOnly,
            rows = 1,
            showValidation,
            validationLabelInfo,
            variableTypes,
        },
        forwardedRef,
    ) {
        const handleChange = useCallback(
            (expression: string) => {
                onValueChange({ expression, language });
            },
            [language, onValueChange],
        );

        const value = useMemo(() => expressionObj.expression, [expressionObj.expression]);

        const placeholderText = usePlaceholder(language);
        const inputProps = useMemo(
            () => ({
                rows,
                cols,
                value,
                language,
                onValueChange: handleChange,
                readOnly,
                ref: forwardedRef,
                editorMode,
                placeholder: readOnly ? undefined : placeholder || placeholderText,
                InputAdornmentEnd: readOnly ? undefined : InputAdornmentEnd || <Info editorConfig={editorConfig} />,
                useAceWorker: editorMode === EditorMode.JsonTemplate ? false : undefined,
            }),
            [
                InputAdornmentEnd,
                cols,
                editorConfig,
                editorMode,
                forwardedRef,
                handleChange,
                language,
                placeholder,
                placeholderText,
                readOnly,
                rows,
                value,
            ],
        );

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
        parseValueOnEditorChange: ({ expression, language }, newLanguage) => {
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
