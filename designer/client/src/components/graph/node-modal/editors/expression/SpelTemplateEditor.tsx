import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { SpelEditorProps } from "./SpelEditor";
import { SpelEditor } from "./SpelEditor";
import { isQuoted } from "./SpelQuotesUtils";
import { EditorMode, EditorType, ExpressionLang, type ExpressionObj } from "./types";

function looksLikeStringLiteral(expr: string): boolean {
    const trimmed = expr.trim();

    if (trimmed === "") return true;

    return isQuoted(expr);
}

export const SpelTemplateEditor = prepareEditor<SpelEditorProps>(
    (props) => {
        const { t } = useTranslation();
        const { expressionObj, rows = 1, ...passProps } = props;

        const language = editorsParameters[EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR].language;

        const value = useMemo(
            () => ({
                expression: expressionObj?.expression,
                language,
            }),
            [expressionObj?.expression, language],
        );

        return <SpelEditor {...passProps} expressionObj={value} rows={rows} editorMode={EditorMode.SpELTemplate} language={language} />;
    },
    {
        parseValueOnEditorChange: ({ expression, language }: ExpressionObj, newLanguage) => {
            if (language !== ExpressionLang.SpELTemplate) {
                return { expression: expression.replace(/^['"]|['"]$/g, ""), language: newLanguage };
            }

            return { expression, language: newLanguage };
        },
        isSwitchableTo: (expressionObj) => {
            if (expressionObj.language !== ExpressionLang.SpELTemplate) {
                return looksLikeStringLiteral(expressionObj.expression);
            }

            return true;
        },
        notSwitchableToHint: () => "There needs to be a literal value provided to switch to string template mode",
    },
);
