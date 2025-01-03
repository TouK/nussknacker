import React, { useMemo } from "react";
import { RawEditor, RawEditorProps } from "./RawEditor";
import { ExpressionLang } from "./types";
import { SimpleEditor } from "./Editor";

//TODO add highlighting for opening and closing braces ('#{' and '}') in brace/mode/spelTemplate.js file
export const SpelTemplateEditor: SimpleEditor<RawEditorProps> = (props: RawEditorProps) => {
    const { expressionObj, rows = 6, ...passProps } = props;

    const value = useMemo(
        () => ({
            expression: expressionObj.expression,
            language: ExpressionLang.SpELTemplate,
        }),
        [expressionObj],
    );

    return <RawEditor {...passProps} expressionObj={value} rows={rows} />;
};
