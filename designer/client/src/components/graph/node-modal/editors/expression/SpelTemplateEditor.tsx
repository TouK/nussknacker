import React, { useMemo } from "react";
import { SpelEditor, SpelEditorProps } from "./SpelEditor";
import { ExpressionLang } from "./types";
import { SimpleEditor } from "./Editor";

const language = ExpressionLang.SpELTemplate;
//TODO add highlighting for opening and closing braces ('#{' and '}') in brace/mode/spelTemplate.js file
export const SpelTemplateEditor: SimpleEditor<SpelEditorProps> = (props: SpelEditorProps) => {
    const { expressionObj, rows = 6, ...passProps } = props;

    const value = useMemo(
        () => ({
            expression: expressionObj.expression,
            language,
        }),
        [expressionObj],
    );

    return <SpelEditor {...passProps} expressionObj={value} rows={rows} language={language} />;
};

SpelTemplateEditor.language = language;
