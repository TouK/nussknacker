import React, { useMemo } from "react";
import RawEditor, { RawEditorProps } from "./RawEditor";
import { ExpressionLang } from "./types";

//TODO add highlighting for opening and closing braces ('#{' and '}') in brace/mode/spelTemplate.js file
const SpelTemplateEditor = (props: RawEditorProps) => {
    const { expressionObj, ...passProps } = props;

    const value = useMemo(
        () => ({
            expression: expressionObj.expression,
            language: ExpressionLang.SpELTemplate,
        }),
        [expressionObj],
    );

    return <RawEditor {...passProps} expressionObj={value} rows={6} />;
};

export default SpelTemplateEditor;
