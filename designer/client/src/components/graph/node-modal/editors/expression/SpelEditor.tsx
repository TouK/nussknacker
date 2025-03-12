import React, { ForwardedRef, forwardRef, ReactNode, useCallback, useMemo } from "react";
import ReactAce from "react-ace/lib/ace";
import { VariableTypes } from "../../../../../types";
import { FieldError } from "../Validators";
import { ExpressionSuggest, ExpressionSuggestProps } from "./ExpressionSuggest";
import { EditorMode, ExpressionLang, ExpressionObj } from "./types";
import { editors, OnValueChange, SimpleEditor } from "./Editor";

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
        language = editors.SpelParameterEditor.language,
    } = props;

    const handleChange = useCallback(
        (expression: string) => {
            onValueChange({ expression, language });
        },
        [language, onValueChange],
    );

    const value = useMemo(() => expressionObj.expression, [expressionObj.expression]);

    const inputProps = useMemo<ExpressionSuggestProps["inputProps"]>(
        () => ({
            rows,
            cols,
            value,
            language,
            onValueChange: handleChange,
            readOnly,
            ref: forwardedRef,
            editorMode,
            placeholder,
        }),
        [rows, cols, value, language, handleChange, readOnly, forwardedRef, editorMode, placeholder],
    );

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
