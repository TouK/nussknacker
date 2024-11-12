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
import { FormControl, FormLabel } from "@mui/material";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";
import { DualParameterEditor } from "./expression/DualParameterEditor";

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
    fieldErrors?: FieldError[];
    variableTypes: VariableTypes;
    validationLabelInfo?: string;
    placeholder?: string;
}

export const EditableEditor = forwardRef((props: Props, ref) => {
    const { expressionObj, valueClassName, param, fieldErrors = [], validationLabelInfo } = props;

    if (param?.editor?.type === "DualParameterEditor" && !param?.editors) {
        param.editors = [];
        param.editors.push({ type: EditorType.RAW_PARAMETER_EDITOR });
        param.editors.push({ type: param.editor.simpleEditor.type });
    }
    const availableEditors = useMemo(
        (): ParamType["editors"] => (isEmpty(param) ? [{ type: EditorType.RAW_PARAMETER_EDITOR }] : param.editors || [param.editor]),
        [param],
    );

    const Editors: (SimpleEditor | ExtendedEditor)[] = useMemo(() => availableEditors.map((editorType) => editors[editorType.type]), []);

    const formatter = useMemo(
        () => (expressionObj.language === ExpressionLang.SpEL ? spelFormatters[param?.typ?.refClazzName] : null),
        [expressionObj.language, param?.typ?.refClazzName],
    );

    if (Editors.length === 1) {
        const Editor = Editors[0];
        return (
            <Editor
                {...props}
                ref={ref}
                editorConfig={param?.editor}
                className={`${valueClassName ? valueClassName : nodeValue}`}
                fieldErrors={fieldErrors}
                formatter={formatter}
                expressionInfo={validationLabelInfo}
            />
        );
    }

    if (Editors.length === 2) {
        debugger
        return (
            <DualParameterEditor
                {...props}
                ref={ref}
                BasicEditor={Editors[0]}
                ExpressionEditor={Editors[1]}
                className={`${valueClassName ? valueClassName : nodeValue}`}
                fieldErrors={fieldErrors}
                formatter={formatter}
                expressionInfo={validationLabelInfo}
            />
        );
    }

    if (Editors.length > 2) {
        throw new Error("We only support maximum two editors for the field. Check your configuration");
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
