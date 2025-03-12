import { isEmpty } from "lodash";
import React, { forwardRef, ReactNode, useMemo } from "react";
import { VariableTypes } from "../../../../types";
import { UnknownFunction } from "../../../../types/common";
import { editors, EditorType, OnValueChange } from "./expression/Editor";
import { spelFormatters } from "./expression/Formatter";
import { ExpressionLang, ExpressionObj } from "./expression/types";
import { ParamType } from "./types";
import { FieldError, PossibleValue } from "./Validators";
import { cx } from "@emotion/css";
import { FormControl, FormLabel } from "@mui/material";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";
import { MultipleEditors } from "./field/MultipleEditors";

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
    onValueChange: OnValueChange;
    fieldErrors?: FieldError[];
    variableTypes: VariableTypes;
    validationLabelInfo?: ReactNode;
    placeholder?: string;
}

export const EditableEditor = forwardRef((props: Props, ref) => {
    const { expressionObj, valueClassName, param, fieldErrors = [], validationLabelInfo } = props;

    const availableEditors = useMemo(
        (): ParamType["editors"] => (isEmpty(param) ? [{ type: EditorType.SPEL_PARAMETER_EDITOR }] : param.editors || [param.editor]),
        [param],
    );

    const formatter = useMemo(
        () => (expressionObj.language === ExpressionLang.SpEL ? spelFormatters[param?.typ?.refClazzName] : null),
        [expressionObj.language, param?.typ?.refClazzName],
    );

    if (availableEditors.length === 1) {
        const singleEditor = availableEditors[0];
        const Editor = editors[singleEditor.type];
        return (
            <Editor
                {...props}
                ref={ref}
                editorConfig={singleEditor}
                className={`${valueClassName ? valueClassName : nodeValue}`}
                fieldErrors={fieldErrors}
                formatter={formatter}
                expressionInfo={validationLabelInfo}
            />
        );
    }

    if (availableEditors.length > 1) {
        return <MultipleEditors {...props} fieldErrors={fieldErrors} />;
    }
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
                {fieldLabel ? renderFieldLabel?.(fieldLabel) : <FormLabel />}
                <EditableEditor {...props} />
            </>
        </FormControl>
    );
}

export default EditableEditorRow;
