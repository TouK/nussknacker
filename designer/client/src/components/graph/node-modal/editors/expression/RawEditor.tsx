import cn from "classnames";
import React, { ForwardedRef, forwardRef, useMemo } from "react";
import ReactAce from "react-ace/lib/ace";
import ExpressionSuggest from "./ExpressionSuggest";
import { NodeValidationError, VariableTypes } from "../../../../../types";
import { EditorMode, ExpressionObj } from "./types";

export type RawEditorProps = {
    expressionObj: ExpressionObj;
    fieldErrors: NodeValidationError[];
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
    const className1 = useMemo(() => cn("node-input"), []);

    const inputProps = useMemo(
        () => ({
            rows: rows,
            cols: cols,
            className: className1,
            value: value,
            language: language,
            onValueChange: onValueChange,
            readOnly: readOnly,
            ref: forwardedRef,
            editorMode: editorMode,
        }),
        [rows, cols, className1, value, language, onValueChange, readOnly, forwardedRef, editorMode],
    );

    return (
        <div className={className}>
            <ExpressionSuggest
                inputProps={inputProps}
                variableTypes={variableTypes}
                fieldErrors={fieldErrors}
                isMarked={isMarked}
                showValidation={showValidation}
                validationLabelInfo={validationLabelInfo}
                editorMode={editorMode}
            />
        </div>
    );
};

export const RawEditor = forwardRef(RawEditorComponent);
