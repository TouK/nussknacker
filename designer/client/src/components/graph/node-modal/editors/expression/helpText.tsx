import { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { OverrideKeys } from "../../parameterHelpers";
import { useParamKey } from "../ParamKeyProvider";
import { ExpressionLang } from "./types";

function mergeTexts(...strings: string[]) {
    return strings.filter(Boolean).join("\n\n");
}

export function useHelpText(language: ExpressionLang) {
    const { t } = useTranslation();
    const paramKey = useParamKey();

    return useMemo(() => {
        switch (language) {
            case ExpressionLang.SpEL: {
                const helpText = t(
                    "editors.spelEditor.infoText",
                    `You are using an expression-based input, allowing calculations and conditions. Access variables and helpers with \`#\`, e.g., \`#input.someField == 'value'\` or \`#UTIL.split('foo-bar', '-')\`. \n 
When accessing variables that support dynamic fields you can use \`#input['dynamicField'].toTargetType\`, e.g. \`#input['accountNo'].toLong\`. \n
Strings need to be quoted; use \`+\` to concatenate multiple strings values. \n
Use autocompletion to explore available options. To read more see [Documentation](https://docs.nussknacker.io/scenariosAuthoring/introduction/#spel).`,
                );
                switch (paramKey) {
                    case OverrideKeys.DecisionTableMatch:
                        return mergeTexts(
                            t(
                                "editors.spelEditor.additionalInfoText.tableMatch",
                                `Match condition is a **Boolean** SpEL expression based on which one or more rows of the decision table will be selected. To create this expression you can refer to the variables used in the scenario and values in the decision table. Use \`#ROW.columnName\` notation to access a single cell value.`,
                            ),
                            helpText,
                        );
                    case OverrideKeys.AggregateEndSession:
                        return mergeTexts(
                            t(
                                "editors.spelEditor.additionalInfoText.endSession",
                                `The Aggregate Session window can close not only on timeout. It will also close when the expression entered in this field evaluates to true. Set this parameter to \`false\` if the only way to close the window is through session timeout.`,
                            ),
                            helpText,
                        );
                    default:
                        return helpText;
                }
            }
            case ExpressionLang.SpELTemplate:
                return t(
                    "editors.spelTemplateEditor.infoText",
                    `You are using a string-template-based input, allowing text with embedded expressions. Text should not be quoted. \n 
Embed expression with \`#{ }\`, e.g., \`Hello #{ #input.name }\`.  \n
When accessing variables that support dynamic fields you can use \`#input['dynamicField'].toTargetType\`, e.g. \`#input['accountNo'].toLong\`. \n
Use autocompletion to explore available options. To read more see [Documentation](https://docs.nussknacker.io/scenariosAuthoring/introduction/#spel)`,
                );
            case ExpressionLang.JsonTemplate:
                return t(
                    "editors.jsonTemplateEditor.infoText",
                    `You are using a json-template-based input, allowing json with embedded expressions. This input behave similar to string-template, with differences:\n
* It produces a data record, which is very useful when you produces some message and want to be sure that it will be in valid format\n
* It supports validations against schema, when used in schema-aware context\n
\n
Embed expression with \`#{ }\`, e.g., \n
\`\`\`jsonTemplate
{
"name": "#{ #input.name }",
"age": #{ #input.age },
#{ #input.secondName ?: ', "secondName": "' + #input.secondName + '"' }
}
\`\`\`\n
In placeholders, you can use more complex types such as records and lists. To make sure that they will be rendered correctly, use \`#CONV.toJsonString(#complexType)\` helper function.\n
\n
When accessing variables that support dynamic fields you can use \`#input['dynamicField'].toTargetType\`, e.g. \`#input['accountNo'].toLong\`.\n
\n
Use autocompletion to explore available options. To read more see [Documentation](https://docs.nussknacker.io/scenariosAuthoring/introduction/#spel)`,
                );
        }
    }, [language, paramKey, t]);
}

export function usePlaceholder(language: ExpressionLang) {
    const { t } = useTranslation();
    switch (language) {
        case ExpressionLang.SpEL:
            return t("editors.spelEditor.placeholder", "e.g. #input.someField");
        case ExpressionLang.SpELTemplate:
            return t("editors.spelTemplateEditor.placeholder", "e.g. Hello #{ #input.someField }");
        case ExpressionLang.JsonTemplate:
            return t("editors.jsonTemplateEditor.placeholder", 'e.g. { "key": "#{ #input.value }" }');
    }
}
