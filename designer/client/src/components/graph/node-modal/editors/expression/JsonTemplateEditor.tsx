import React, { useMemo } from "react";

import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { Info, InputAdornmentEndColumn } from "./InputAdornmentEndColumn";
import { ResetToDefault } from "./ResetToDefaultButton";
import type { SpelEditorProps } from "./SpelEditor";
import { SpelEditor } from "./SpelEditor";
import { addQuotesToExpression } from "./SpelQuotesUtils";
import { EditorMode, EditorType, ExpressionLang, type ExpressionObj } from "./types";

export const JsonTemplateEditor = prepareEditor<SpelEditorProps>(
    (props) => {
        const { expressionObj, rows = 5, ...passProps } = props;

        const language = editorsParameters[EditorType.JSON_TEMPLATE_PARAMETER_EDITOR].language;

        const value = useMemo(
            () => ({
                expression: expressionObj.expression,
                language,
            }),
            [expressionObj.expression, language],
        );

        return (
            <SpelEditor
                inputAdornmentEnd={
                    <InputAdornmentEndColumn>
                        <Info editorConfig={props.editorConfig} />
                        <ResetToDefault
                            value={expressionObj?.expression}
                            defaultValue={props.defaultValue}
                            handleChange={props.onValueChange}
                        />
                    </InputAdornmentEndColumn>
                }
                {...passProps}
                expressionObj={value}
                rows={rows}
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
