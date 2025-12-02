import React, { useMemo } from "react";

import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { SpelEditorProps } from "./SpelEditor";
import { SpelEditor } from "./SpelEditor";
import { addQuotesToExpression } from "./SpelQuotesUtils";
import { EditorMode, EditorType, ExpressionLang, type ExpressionObj } from "./types";

export const JsonTemplateEditor = prepareEditor<SpelEditorProps>(
    (props) => {
        const { expressionObj, rows = 5, ...passProps } = props;

        const value = useMemo(
            () => ({
                expression: expressionObj.expression,
                language: editorsParameters[EditorType.JSON_TEMPLATE_PARAMETER_EDITOR].language,
            }),
            [expressionObj],
        );

        return (
            <SpelEditor
                {...passProps}
                expressionObj={value}
                rows={rows}
                editorMode={EditorMode.JsonTemplate}
                language={editorsParameters[EditorType.JSON_TEMPLATE_PARAMETER_EDITOR].language}
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
