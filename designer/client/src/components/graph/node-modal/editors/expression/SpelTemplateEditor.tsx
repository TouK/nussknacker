import React, { useMemo } from "react";

import type { ExtendedEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { SpelEditorProps } from "./SpelEditor";
import { SpelEditor } from "./SpelEditor";
import { EditorMode, ExpressionLang, type ExpressionObj } from "./types";

export const SpelTemplateEditor: ExtendedEditor<SpelEditorProps> = (props: SpelEditorProps) => {
    const { expressionObj, rows = 1, ...passProps } = props;

    const value = useMemo(
        () => ({
            expression: expressionObj?.expression,
            language: editorsParameters.SpelTemplateParameterEditor.language,
        }),
        [expressionObj],
    );

    return (
        <SpelEditor
            {...passProps}
            expressionObj={value}
            rows={rows}
            editorMode={EditorMode.SpELTemplate}
            language={editorsParameters.SpelTemplateParameterEditor.language}
        />
    );
};
SpelTemplateEditor.parseValueOnEditorChange = ({ expression, language }: ExpressionObj, newLanguage) => {
    if (language === ExpressionLang.SpEL) {
        return { expression: expression.replace(/^['"]|['"]$/g, ""), language: newLanguage };
    }

    return { expression, language: newLanguage };
};

function looksLikeStringLiteral(expr: string): boolean {
    const trimmed = expr.trim();

    if (trimmed === "") return true;

    const singleQuoted = /^'(?:\\'|''|[^'])*'$/.test(trimmed);
    const doubleQuoted = /^"(?:\\"|""|[^"])*"$/.test(trimmed);

    return singleQuoted || doubleQuoted;
}

SpelTemplateEditor.isSwitchableTo = (expressionObj) => {
    if (expressionObj.language === ExpressionLang.SpEL) {
        return looksLikeStringLiteral(expressionObj.expression);
    }

    return true;
};
SpelTemplateEditor.notSwitchableToHint = () => "The expression must be a literal value to switch to string template mode";
