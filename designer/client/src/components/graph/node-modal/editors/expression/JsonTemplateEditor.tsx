import React, { useCallback, useMemo } from "react";

import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { Info, InputAdornmentEndColumn } from "./InputAdornmentEndColumn";
import type { SpelEditorProps } from "./SpelEditor";
import { SpelEditor } from "./SpelEditor";
import { addQuotesToExpression } from "./SpelQuotesUtils";
import { EditorMode, EditorType, ExpressionLang, type ExpressionObj } from "./types";
import { useAceEditorAdornment } from "./useAceEditorAdornment";

export const JsonTemplateEditor = prepareEditor<SpelEditorProps>(
    (props) => {
        const { expressionObj, rows = 5, inputAdornmentEnd, readOnly, onValueChange, defaultValue, ...passProps } = props;

        const language = editorsParameters[EditorType.JSON_TEMPLATE_PARAMETER_EDITOR].language;

        const onChange = useCallback(
            (expression: string) => {
                onValueChange({ expression, language });
            },
            [language, onValueChange],
        );

        const { editorRef, maxLines, resetToDefaultButton, fullscreenButton } = useAceEditorAdornment({
            value: expressionObj.expression,
            defaultValue,
            readOnly,
            onChange,
        });

        const value = useMemo(
            () => ({
                expression: expressionObj.expression,
                language,
            }),
            [expressionObj.expression, language],
        );

        return (
            <SpelEditor
                ref={editorRef}
                inputAdornmentEnd={
                    <InputAdornmentEndColumn>
                        <Info editorConfig={props.editorConfig} />
                        {inputAdornmentEnd || resetToDefaultButton}
                        {fullscreenButton}
                    </InputAdornmentEndColumn>
                }
                {...passProps}
                readOnly={readOnly}
                onValueChange={onValueChange}
                defaultValue={defaultValue}
                expressionObj={value}
                rows={rows}
                maxLines={maxLines}
                editorMode={EditorMode.JsonTemplate}
                language={language}
            />
        );
    },
    {
        parseValueOnEditorChange: ({ expression, language }: ExpressionObj, newLanguage) => {
            if (language === ExpressionLang.SpELTemplate) {
                return { expression: addQuotesToExpression({ expression, language: newLanguage }), language: newLanguage };
            }

            return { expression, language: newLanguage };
        },
        isSwitchableTo: () => {
            return true;
        },
        notSwitchableToHint: () => "",
    },
);
