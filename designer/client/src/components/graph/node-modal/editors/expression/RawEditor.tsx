import React, { ForwardedRef, forwardRef, useMemo } from "react";
import ReactAce from "react-ace/lib/ace";
import { ExpressionSuggest } from "./ExpressionSuggest";
import { VariableTypes } from "../../../../../types";
import { EditorMode, ExpressionObj } from "./types";
import { NodeInputCss } from "../../../../NodeInput";
import { useTheme } from "@mui/material";
import { cx } from "@emotion/css";
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

    const theme = useTheme();
    const value = useMemo(() => expressionObj.expression, [expressionObj.expression]);
    const language = useMemo(() => expressionObj.language, [expressionObj.language]);

    const inputProps = useMemo(
        () => ({
            rows: rows,
            cols: cols,
            className: cx("node-input"),
            style: NodeInputCss(),
            value: value,
            language: language,
            onValueChange: onValueChange,
            readOnly: readOnly,
            ref: forwardedRef,
            editorMode: editorMode,
        }),
        [rows, cols, theme, value, language, onValueChange, readOnly, forwardedRef, editorMode],
    );

    return (
        <div className={className} style={{ width: "100%" }}>
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
