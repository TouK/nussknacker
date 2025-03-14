import React, { ForwardedRef, forwardRef, useMemo } from "react";
import ReactAce from "react-ace/lib/ace";
import { VariableTypes } from "../../../../../types";
import { FieldError } from "../Validators";
import { ExpressionSuggest, ExpressionSuggestProps } from "./ExpressionSuggest";
import { InfoTooltip } from "./InfoTooltip";
import { EditorMode, ExpressionLang, ExpressionObj } from "./types";
import { useTranslation } from "react-i18next";

const spelEditorInfoText = `You are using an expression-based approach, allowing calculations and conditions. Access variables with **#**, e.g., **#input.someField == 'value'**. \n 
Use **#input['dynamicField'].toTargetType** for dynamic fields. Helpers (e.g., **#UTILS**) provide additional functionality.  \n
Strings need to be quoted; use **+** to concatenate strings. \n
Use autocompletion to explore available options. To read more see [Documentation](https://nussknacker.io/documentation/docs/scenarios_authoring/Spel).`;

const spelTemplateEditorInfoText = `You are using a string-template-based approach, allowing text with embedded expressions. Text should not be quoted. \n 
Embed expression with **#{ }**, e.g., Hello **#{ #input.name }**. For dynamic fields, use **#input['dynamicField'].toTargetType**. \n
You can also use built-in helpers like **#UTILS** for additional functionality. \n
Use autocompletion for available options. To read more see [Documentation](https://nussknacker.io/documentation/docs/scenarios_authoring/Spel)`;

export type RawEditorProps = {
    expressionObj: ExpressionObj;
    fieldErrors: FieldError[];
    isMarked?: boolean;
    showValidation?: boolean;
    readOnly?: boolean;
    onValueChange: (value: string) => void;
    rows?: number;
    cols?: number;
    className?: string;
    variableTypes: VariableTypes;
    validationLabelInfo?: string;
    editorMode?: EditorMode;
    placeholder?: string;
};

const RawEditorComponent = (props: RawEditorProps, forwardedRef: ForwardedRef<ReactAce>) => {
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
    } = props;

    const value = useMemo(() => expressionObj.expression, [expressionObj.expression]);
    const language = useMemo(() => expressionObj.language, [expressionObj.language]);

    const inputProps = useMemo<ExpressionSuggestProps["inputProps"]>(() => {
        const properties: ExpressionSuggestProps["inputProps"] = {
            rows: rows,
            cols: cols,
            value: value,
            language: language,
            onValueChange: onValueChange,
            readOnly: readOnly,
            ref: forwardedRef,
            editorMode: editorMode,
        };

        if (expressionObj.language === ExpressionLang.SpEL) {
            properties.placeholder = placeholder || t("editors.spelEditor.placeholder", "e.g. #input.someField");
            properties.InputAdornmentEnd = <InfoTooltip text={t("editors.spelEditor.infoText", spelEditorInfoText)} />;
        }

        if (editorMode === EditorMode.SpELTemplate) {
            properties.placeholder = placeholder || t("editors.spelTemplateEditor.placeholder", "e.g. Hello #{ #input.someField }");
            properties.InputAdornmentEnd = <InfoTooltip text={t("editors.spelTemplateEditor.infoText", spelTemplateEditorInfoText)} />;
        }

        return properties;
    }, [rows, cols, value, language, onValueChange, readOnly, forwardedRef, editorMode, expressionObj.language, placeholder, t]);

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

export const RawEditor = forwardRef(RawEditorComponent);
