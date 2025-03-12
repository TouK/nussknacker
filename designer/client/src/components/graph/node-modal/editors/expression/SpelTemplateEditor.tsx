import React, { useMemo } from "react";
import { RawEditor, RawEditorProps } from "./RawEditor";
import { ExpressionLang } from "./types";
import { SimpleEditor } from "./Editor";
import { useTranslation } from "react-i18next";

const spelTemplateEditorInfoText = `You are using a string-template-based approach, allowing text with embedded expressions. Text should not be quoted. \n 
Embed expression with \`#{ }\`, e.g., Hello \`#{ #input.name }\`. For dynamic fields, use \`#input['dynamicField'].toTargetType\`. \n
You can also use built-in helpers like \`#UTILS\` for additional functionality. \n
Use autocompletion for available options. To read more see [Documentation](https://nussknacker.io/documentation/docs/scenarios_authoring/Spel)`;

//TODO add highlighting for opening and closing braces ('#{' and '}') in brace/mode/spelTemplate.js file
export const SpelTemplateEditor: SimpleEditor<RawEditorProps> = (props: RawEditorProps) => {
    const { t } = useTranslation();
    const { expressionObj, ...passProps } = props;

    const value = useMemo(
        () => ({
            expression: expressionObj.expression,
            language: ExpressionLang.SpELTemplate,
        }),
        [expressionObj],
    );

    return (
        <RawEditor
            {...passProps}
            expressionObj={value}
            rows={6}
            placeholder={t("editors.spelTemplateEditor.placeholder", "e.g. Hello #{ #input.someField }")}
            infoText={t("editors.spelTemplateEditor.infoText", spelTemplateEditorInfoText)}
        />
    );
};
