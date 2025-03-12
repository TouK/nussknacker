import React, { useMemo } from "react";
import { SpelEditor, SpelEditorProps } from "./SpelEditor";
import { editors, SimpleEditor } from "./Editor";

//TODO add highlighting for opening and closing braces ('#{' and '}') in brace/mode/spelTemplate.js file
export const SpelTemplateEditor: SimpleEditor<SpelEditorProps> = (props: SpelEditorProps) => {
    const { expressionObj, rows = 6, ...passProps } = props;

    const value = useMemo(
        () => ({
            expression: expressionObj.expression,
            language: editors.SpelTemplateParameterEditor.language,
        }),
        [expressionObj],
    );

    return <SpelEditor {...passProps} expressionObj={value} rows={rows} language={editors.SpelTemplateParameterEditor.language} />;
};
