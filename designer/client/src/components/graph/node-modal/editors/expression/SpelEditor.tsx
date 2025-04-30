import type { ForwardedRef, ReactNode } from "react";
import React, { forwardRef, useCallback, useMemo } from "react";
import type ReactAce from "react-ace/lib/ace";
import { useTranslation } from "react-i18next";

import type { VariableTypes } from "../../../../../types";
import { InfoTooltip } from "../InfoTooltip";
import type { FieldError } from "../Validators";
import type { OnValueChange, SimpleEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { ExpressionSuggestProps } from "./ExpressionSuggest";
import { ExpressionSuggest } from "./ExpressionSuggest";
import type { ExpressionObj } from "./types";
import { EditorMode, ExpressionLang } from "./types";

export type SpelEditorProps = {
    expressionObj: ExpressionObj;
    fieldErrors: FieldError[];
    isMarked?: boolean;
    showValidation?: boolean;
    readOnly?: boolean;
    onValueChange: OnValueChange;
    rows?: number;
    cols?: number;
    className?: string;
    variableTypes: VariableTypes;
    validationLabelInfo?: ReactNode;
    editorMode?: EditorMode;
    placeholder?: string;
    language?: ExpressionLang;
    infoText?: string;
};

const SpelEditorComponent = (props: SpelEditorProps, forwardedRef: ForwardedRef<ReactAce>) => {
    const { t } = useTranslation();
    const {
        expressionObj,
        fieldErrors,
        isMarked,
        showValidation,
        readOnly,
        onValueChange,
        rows = 1,
        cols = 50,
        className,
        variableTypes,
        validationLabelInfo,
        editorMode,
        placeholder,
        language = editorsParameters.SpelParameterEditor.language,
    } = props;

    const handleChange = useCallback(
        (expression: string) => {
            onValueChange({ expression, language });
        },
        [language, onValueChange],
    );

    const value = useMemo(() => expressionObj.expression, [expressionObj.expression]);

    const inputProps = useMemo<ExpressionSuggestProps["inputProps"]>(() => {
        const properties: ExpressionSuggestProps["inputProps"] = {
            rows,
            cols,
            value,
            language,
            onValueChange: handleChange,
            readOnly,
            ref: forwardedRef,
            editorMode: editorMode,
        };

        if (expressionObj.language === ExpressionLang.SpEL && !readOnly) {
            properties.placeholder = placeholder || t("editors.spelEditor.placeholder", "e.g. #input.someField");
            properties.InputAdornmentEnd = (
                <InfoTooltip
                    customComponentsProps={{
                        tooltip: { sx: { maxWidth: "none" } },
                    }}
                    title={t(
                        "editors.spelEditor.infoText",
                        `You are using an expression-based input, allowing calculations and conditions. Access variables and helpers with \`#\`, e.g., \`#input.someField == 'value'\` or \`#UTIL.split('foo-bar', '-')\`. \n 
When accessing variables that support dynamic fields you can use \`#input['dynamicField'].toTargetType\`, e.g. \`#input['accountNo'].toLong\`. \n
Strings need to be quoted; use \`+\` to concatenate multiple strings values. \n
Use autocompletion to explore available options. To read more see [Documentation](https://nussknacker.io/documentation/docs/scenarios_authoring/Spel).`,
                    )}
                />
            );
        }

        if (editorMode === EditorMode.SpELTemplate && !readOnly) {
            properties.placeholder = placeholder || t("editors.spelTemplateEditor.placeholder", "e.g. Hello #{ #input.someField }");
            properties.InputAdornmentEnd = (
                <InfoTooltip
                    customComponentsProps={{
                        tooltip: { sx: { maxWidth: "none" } },
                    }}
                    title={t(
                        "editors.spelTemplateEditor.infoText",
                        `You are using a string-template-based input, allowing text with embedded expressions. Text should not be quoted. \n 
Embed expression with \`#{ }\`, e.g., \`Hello #{ #input.name }\`. When accessing variables that support dynamic fields you can use \`#input['dynamicField'].toTargetType\`, e.g. \`#input['accountNo'].toLong\`. \n
Use autocompletion to explore available options. To read more see [Documentation](https://nussknacker.io/documentation/docs/scenarios_authoring/Spel)`,
                    )}
                />
            );
        }

        if (editorMode === EditorMode.JsonTemplate && !readOnly) {
            properties.placeholder = placeholder || t("editors.jsonTemplateEditor.placeholder", 'e.g. { "key": "#{ #input.value }" }');
            properties.InputAdornmentEnd = (
                <InfoTooltip
                    title={t(
                        "editors.jsonTemplateEditor.infoText",
                        `You are using a json-template-based input, allowing json with embedded expressions. \n 
Embed expression with \`#{ }\`, e.g., \`{ "name": #{ #input.name } }\`. When accessing variables that support dynamic fields you can use \`#input['dynamicField'].toTargetType\`, e.g. \`#input['accountNo'].toLong\`. \n
Use autocompletion to explore available options. To read more see [Documentation](https://nussknacker.io/documentation/docs/scenarios_authoring/Spel)`,
                    )}
                />
            );
        }
        if (editorMode === EditorMode.JsonTemplate) {
            properties.useAceWorker = false;
        }

        return properties;
    }, [rows, cols, value, language, handleChange, readOnly, forwardedRef, editorMode, expressionObj.language, placeholder, t]);

    return (
        <ExpressionSuggest
            className={className}
            inputProps={inputProps}
            variableTypes={variableTypes}
            fieldErrors={fieldErrors}
            isMarked={isMarked}
            showValidation={showValidation}
            validationLabelInfo={validationLabelInfo}
        />
    );
};

export const SpelEditor: SimpleEditor<SpelEditorProps> = forwardRef(SpelEditorComponent) as SimpleEditor<SpelEditorProps>;
