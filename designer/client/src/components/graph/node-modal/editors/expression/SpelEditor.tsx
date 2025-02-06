import React, { ForwardedRef, forwardRef, ReactNode, useCallback, useMemo } from "react";
import ReactAce from "react-ace/lib/ace";
import { VariableTypes } from "../../../../../types";
import { FieldError } from "../Validators";
import { ExpressionSuggest, ExpressionSuggestProps } from "./ExpressionSuggest";
import { InfoTooltip } from "./InfoTooltip";
import { EditorMode, ExpressionLang, ExpressionObj } from "./types";
import { useTranslation } from "react-i18next";
import { OnValueChange, SimpleEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";

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

    const { expression: value = "", language = ExpressionLang.SpEL } = expressionObj || {};

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
                    text={t(
                        "editors.spelEditor.infoText",
                        `You are using an expression-based approach, allowing calculations and conditions. Access variables with **#**, e.g., **#input.someField == 'value'**. \n 
Use **#input['dynamicField'].toTargetType** for dynamic fields. Helpers (e.g., **#UTILS**) provide additional functionality.  \n
Strings need to be quoted; use **+** to concatenate strings. \n
Use autocompletion to explore available options. To read more see [Documentation](https://nussknacker.io/documentation/docs/scenarios_authoring/Spel).`,
                    )}
                />
            );
        }

        if (editorMode === EditorMode.SpELTemplate && !readOnly) {
            properties.placeholder = placeholder || t("editors.spelTemplateEditor.placeholder", "e.g. Hello #{ #input.someField }");
            properties.InputAdornmentEnd = (
                <InfoTooltip
                    text={t(
                        "editors.spelTemplateEditor.infoText",
                        `You are using a string-template-based approach, allowing text with embedded expressions. Text should not be quoted. \n 
Embed expression with **#{ }**, e.g., Hello **#{ #input.name }**. For dynamic fields, use **#input['dynamicField'].toTargetType**. \n
You can also use built-in helpers like **#UTILS** for additional functionality. \n
Use autocompletion for available options. To read more see [Documentation](https://nussknacker.io/documentation/docs/scenarios_authoring/Spel)`,
                    )}
                />
            );
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
