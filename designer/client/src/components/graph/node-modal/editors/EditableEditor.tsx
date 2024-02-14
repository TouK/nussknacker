import { isEmpty } from "lodash";
import React, { forwardRef, useMemo } from "react";
import { VariableTypes } from "../../../../types";
import { UnknownFunction } from "../../../../types/common";
import { editors, EditorType, ExtendedEditor, SimpleEditor } from "./expression/Editor";
import { spelFormatters } from "./expression/Formatter";
import { ExpressionLang, ExpressionObj } from "./expression/types";
import { ParamType } from "./types";
import { FieldError, PossibleValue } from "./Validators";
import { cx } from "@emotion/css";
import { FormControl } from "@mui/material";
import ErrorBoundary from "../../../common/ErrorBoundary";

interface Props {
    expressionObj: ExpressionObj;
    showSwitch?: boolean;
    fieldLabel?: string;
    readOnly?: boolean;
    valueClassName?: string;
    param?: ParamType;
    values?: Array<PossibleValue>;
    isMarked?: boolean;
    showValidation?: boolean;
    onValueChange: (value: string) => void;
    fieldErrors: FieldError[];
    variableTypes: VariableTypes;
    validationLabelInfo?: string;
    placeholder?: string;
}

export const EditableEditor = forwardRef((props: Props, ref) => {
    const { expressionObj, valueClassName, param, fieldErrors, validationLabelInfo } = props;

    const editorType = useMemo(() => (isEmpty(param) ? EditorType.RAW_PARAMETER_EDITOR : param.editor.type), [param]);

    const Editor: SimpleEditor | ExtendedEditor = useMemo(() => editors[editorType], [editorType]);

    const formatter = useMemo(
        () => (expressionObj.language === ExpressionLang.SpEL ? spelFormatters[param?.typ?.refClazzName] : null),
        [expressionObj.language, param?.typ?.refClazzName],
    );

    return (
        <ErrorBoundary>
            <Editor
                {...props}
                ref={ref}
                editorConfig={param?.editor}
                className={`${valueClassName ? valueClassName : "node-value"}`}
                fieldErrors={fieldErrors}
                formatter={formatter}
                expressionInfo={validationLabelInfo}
            />
        </ErrorBoundary>
    );
});

EditableEditor.displayName = "EditableEditor";

function EditableEditorRow({
    rowClassName,
    renderFieldLabel,
    fieldLabel,
    ...props
}: Props & {
    rowClassName?: string;
    renderFieldLabel?: UnknownFunction;
}): JSX.Element {
    return (
        <FormControl className={cx(rowClassName && rowClassName)} style={{ width: "100%", margin: rowClassName && 0 }}>
            <>
                {fieldLabel && renderFieldLabel?.(fieldLabel)}
                <EditableEditor {...props} />
            </>
        </FormControl>
    );
}

export default EditableEditorRow;
