import { isEmpty } from "lodash";
import React, { useMemo } from "react";
import { NodeValidationError, VariableTypes } from "../../../../types";
import { UnknownFunction } from "../../../../types/common";
import { editors, EditorType, ExtendedEditor, SimpleEditor } from "./expression/Editor";
import { spelFormatters } from "./expression/Formatter";
import { ExpressionLang, ExpressionObj } from "./expression/types";
import { ParamType } from "./types";

interface Props {
    expressionObj: ExpressionObj;
    showSwitch?: boolean;
    fieldLabel?: string;
    readOnly?: boolean;
    valueClassName?: string;
    param?: ParamType;
    fieldName?: string;
    isMarked?: boolean;
    showValidation: boolean;
    onValueChange: (value: string) => void;
    fieldErrors: NodeValidationError[];
    variableTypes: VariableTypes;
    validationLabelInfo?: string;
}

export function EditableEditor(props: Props): JSX.Element {
    const { expressionObj, valueClassName, param, fieldName, validationLabelInfo, fieldErrors = [] } = props;

    const editorType = useMemo(() => (isEmpty(param) ? EditorType.RAW_PARAMETER_EDITOR : param.editor.type), [param]);

    const Editor: SimpleEditor | ExtendedEditor = useMemo(() => editors[editorType], [editorType]);

    console.log("fieldName", fieldName);
    const formatter = useMemo(
        () => (expressionObj.language === ExpressionLang.SpEL ? spelFormatters[param?.typ?.refClazzName] : null),
        [expressionObj.language, param?.typ?.refClazzName],
    );

    const validationErrorsForField = fieldErrors.filter((error) => error.fieldName === fieldName);
    return (
        <Editor
            {...props}
            editorConfig={param?.editor}
            className={`${valueClassName ? valueClassName : "node-value"}`}
            fieldErrors={validationErrorsForField}
            formatter={formatter}
            expressionInfo={validationLabelInfo}
        />
    );
}

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
        <div className={`${rowClassName ? rowClassName : " node-row"}`}>
            <>
                {fieldLabel && renderFieldLabel?.(fieldLabel)}
                <EditableEditor {...props} />
            </>
        </div>
    );
}

export default EditableEditorRow;
