import i18next from "i18next";
import React from "react";

import { getAst } from "../../aggregate/pareserHelpers";
import Input from "../field/Input";
import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { FormatterType, spelFormatters, typeFormatters } from "./Formatter";
import { EditorType, ExpressionLang } from "./types";

export const StaticStringEditor = prepareEditor(
    (props) => {
        const { expressionObj, onValueChange, formatter, ...passProps } = props;
        const stringFormatter = formatter == null ? typeFormatters[FormatterType.String] : formatter;

        return (
            <Input
                {...passProps}
                onChange={(event) =>
                    onValueChange({
                        expression: stringFormatter.encode(event.target.value),
                        language: editorsParameters[EditorType.STATIC_STRING_PARAMETER_EDITOR].language,
                    })
                }
                value={stringFormatter.decode(expressionObj.expression) as string}
            />
        );
    },
    {
        isSwitchableTo: ({ expression, language }) => {
            if (language === ExpressionLang.SpEL) {
                return getAst(expression, false)?.type === "StringLiteral";
            }
            return true;
        },
        notSwitchableToHint: () =>
            i18next.t("editors.string.notSwitchableToHint", "Expression must be string literal to switch to {{editorName}} mode", {
                editorName: editorsParameters[EditorType.STATIC_STRING_PARAMETER_EDITOR].displayName,
            }),
        parseValueOnEditorChange: ({ expression, language }, nextLanguage) => {
            if (language === ExpressionLang.SpEL) {
                const nextExpression = spelFormatters[FormatterType.String].decode(expression) as string;
                return { expression: nextExpression, language: nextLanguage };
            }
            return { expression, language: nextLanguage };
        },
    },
);
