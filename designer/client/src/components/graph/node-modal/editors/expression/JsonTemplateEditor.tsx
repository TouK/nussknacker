import React, { useMemo } from "react";

import type { ExtendedEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { SpelEditorProps } from "./SpelEditor";
import { SpelEditor } from "./SpelEditor";
import { addQuotes } from "./SpelQuotesUtils";
import { EditorMode, ExpressionLang, type ExpressionObj } from "./types";

export const JsonTemplateEditor: ExtendedEditor<SpelEditorProps> = (props: SpelEditorProps) => {
    const { expressionObj, rows = 5, ...passProps } = props;

    const value = useMemo(
        () => ({
            expression: expressionObj.expression,
            language: editorsParameters.JsonTemplateParameterEditor.language,
        }),
        [expressionObj],
    );

    return (
        <SpelEditor
            {...passProps}
            expressionObj={value}
            rows={rows}
            editorMode={EditorMode.JsonTemplate}
            language={editorsParameters.JsonTemplateParameterEditor.language}
        />
    );
};

JsonTemplateEditor.parseValueOnEditorChange = ({ expression, language }: ExpressionObj, newLanguage) => {
    if (language === ExpressionLang.SpELTemplate) {
        return { expression: addQuotes(expression), language: newLanguage };
    }

    return { expression, language: newLanguage };
};

JsonTemplateEditor.isSwitchableTo = () => {
    return true;
};

JsonTemplateEditor.notSwitchableToHint = () => "";
