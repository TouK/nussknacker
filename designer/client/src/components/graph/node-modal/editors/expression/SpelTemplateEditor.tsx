import React, { useMemo } from "react";

import type { ExtendedEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { SpelEditorProps } from "./SpelEditor";
import { SpelEditor } from "./SpelEditor";
import { isQuoted } from "./SpelQuotesUtils";
import { EditorMode, EditorType, ExpressionLang, type ExpressionObj } from "./types";

export const SpelTemplateEditor: ExtendedEditor<SpelEditorProps> = (props: SpelEditorProps) => {
    const { expressionObj, rows = 1, ...passProps } = props;

    const value = useMemo(
        () => ({
            expression: expressionObj?.expression,
            language: editorsParameters[EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR].language,
        }),
        [expressionObj],
    );

    return (
        <SpelEditor
            {...passProps}
            expressionObj={value}
            rows={rows}
            editorMode={EditorMode.SpELTemplate}
            language={editorsParameters[EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR].language}
        />
    );
};
SpelTemplateEditor.parseValueOnEditorChange = ({ expression, language }: ExpressionObj, newLanguage) => {
    if (language !== ExpressionLang.SpELTemplate) {
        return { expression: expression.replace(/^['"]|['"]$/g, ""), language: newLanguage };
    }

    return { expression, language: newLanguage };
};

function looksLikeStringLiteral(expr: string): boolean {
    const trimmed = expr.trim();

    if (trimmed === "") return true;

    return isQuoted(expr);
}

SpelTemplateEditor.isSwitchableTo = (expressionObj) => {
    if (expressionObj.language !== ExpressionLang.SpELTemplate) {
        return looksLikeStringLiteral(expressionObj.expression);
    }

    return true;
};
SpelTemplateEditor.notSwitchableToHint = () => "There needs to be a literal value provided to switch to string template mode";
