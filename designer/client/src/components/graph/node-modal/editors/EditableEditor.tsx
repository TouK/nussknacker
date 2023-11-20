import { isEmpty } from "lodash";
import React, { forwardRef, useMemo } from "react";
import { VariableTypes } from "../../../../types";
import { UnknownFunction } from "../../../../types/common";
import { editors, EditorType, simpleEditorValidators } from "./expression/Editor";
import { spelFormatters } from "./expression/Formatter";
import { ExpressionLang, ExpressionObj } from "./expression/types";
import { ParamType } from "./types";
import { Error, PosibleValues, Validator } from "./Validators";
import { NodeRow } from "../NodeDetailsContent/NodeStyled";
import { cx } from "@emotion/css";

interface Props {
    expressionObj: ExpressionObj;
    showSwitch?: boolean;
    fieldLabel?: string;
    readOnly?: boolean;
    valueClassName?: string;
    param?: ParamType;
    values?: Array<PosibleValues>;
    fieldName?: string;
    isMarked?: boolean;
    showValidation?: boolean;
    onValueChange: (value: string) => void;
    errors?: Error[];
    variableTypes: VariableTypes;
    validationLabelInfo?: string;
    validators?: Validator[];
}

export const EditableEditor = forwardRef((props: Props, ref) => {
    const { expressionObj, valueClassName, param, fieldLabel, errors, fieldName, validationLabelInfo, validators = [] } = props;

    const editorType = useMemo(() => (isEmpty(param) ? EditorType.RAW_PARAMETER_EDITOR : param.editor.type), [param]);

    const Editor = useMemo(() => editors[editorType], [editorType]);

    const mergedValidators = validators.concat(simpleEditorValidators(param, errors, fieldName, fieldLabel));

    const formatter = useMemo(
        () => (expressionObj.language === ExpressionLang.SpEL ? spelFormatters[param?.typ?.refClazzName] : null),
        [expressionObj.language, param?.typ?.refClazzName],
    );

    return (
        <Editor
            {...props}
            ref={ref}
            editorConfig={param?.editor}
            className={`${valueClassName ? valueClassName : "node-value"}`}
            validators={mergedValidators}
            formatter={formatter}
            expressionInfo={validationLabelInfo}
        />
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
        <NodeRow className={cx(rowClassName && rowClassName)} style={{ width: "100%", margin: rowClassName && 0 }}>
            <>
                {fieldLabel && renderFieldLabel?.(fieldLabel)}
                <EditableEditor {...props} />
            </>
        </NodeRow>
    );
}

export default EditableEditorRow;
