import React, { ForwardedRef, forwardRef, useMemo } from "react";
import ReactAce from "react-ace/lib/ace";
import { ExpressionSuggest, ExpressionSuggestProps } from "./ExpressionSuggest";
import { VariableTypes } from "../../../../../types";
import { EditorMode, ExpressionObj } from "./types";
import { FieldError } from "../Validators";

export type RawEditorProps = {
    expressionObj: ExpressionObj;
    fieldErrors: FieldError[];
    isMarked?: boolean;
    showValidation: boolean;
    readOnly?: boolean;
    onValueChange: (value: string) => void;
    rows?: number;
    cols?: number;
    className: string;
    variableTypes: VariableTypes;
    validationLabelInfo?: string;
    editorMode?: EditorMode;
};

const RawEditorComponent = (props: RawEditorProps, forwardedRef: ForwardedRef<ReactAce>) => {
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
    } = props;

    const value = useMemo(() => expressionObj.expression, [expressionObj.expression]);
    const language = useMemo(() => expressionObj.language, [expressionObj.language]);

    const inputProps = useMemo<ExpressionSuggestProps["inputProps"]>(
        () => ({
            rows: rows,
            cols: cols,
            value: value,
            language: language,
            onValueChange: onValueChange,
            readOnly: readOnly,
            ref: forwardedRef,
            editorMode: editorMode,
        }),
        [rows, cols, value, language, onValueChange, readOnly, forwardedRef, editorMode],
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

export const RawEditor = forwardRef(RawEditorComponent);
