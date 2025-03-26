import { cx } from "@emotion/css";
import { FormControl, FormLabel } from "@mui/material";
import { isEmpty } from "lodash";
import type { ReactNode } from "react";
import React, { forwardRef, useMemo } from "react";

import type { VariableTypes } from "../../../../types";
import type { UnknownFunction } from "../../../../types/common";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";
import type { OnValueChange } from "./expression/Editor";
import { editors } from "./expression/Editor";
import { spelFormatters } from "./expression/Formatter";
import type { ExpressionObj } from "./expression/types";
import { EditorType, ExpressionLang } from "./expression/types";
import { FieldSwitch } from "./field/FieldSwitch";
import type { ParamType } from "./types";
import type { FieldError, PossibleValue } from "./Validators";

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

    const availableEditors: ParamType["editors"] = useMemo(
        (): ParamType["editors"] => (isEmpty(param) ? [{ type: EditorType.SPEL_PARAMETER_EDITOR }] : param.editors || [param.editor]),
        [param],
    );

    const formatter = useMemo(
        () => (expressionObj?.language === ExpressionLang.SpEL ? spelFormatters[param?.typ?.refClazzName] : null),
        [expressionObj?.language, param?.typ?.refClazzName],
    );

    return (
        <FieldSwitch
            availableEditors={availableEditors}
            expressionObj={expressionObj}
            onValueChange={props.onValueChange}
            readOnly={props.readOnly}
            showSwitch={props.showSwitch}
        >
            {(selectedEditor) => {
                const Editor = editors[selectedEditor.type];

                return (
                    <Editor
                        {...props}
                        ref={ref}
                        editorConfig={selectedEditor}
                        className={`${valueClassName ? valueClassName : nodeValue}`}
                        fieldErrors={fieldErrors}
                        formatter={formatter}
                        expressionInfo={validationLabelInfo}
                    />
                );
            }}
        </FieldSwitch>
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
        <FormControl
            className={cx(rowClassName && rowClassName)}
            sx={{
                width: "100%",
                margin: rowClassName && 0,
            }}
        >
            <>
                {fieldLabel ? renderFieldLabel?.(fieldLabel) : <FormLabel />}
                <EditableEditor {...props} />
            </>
        </FormControl>
    );
}

export default EditableEditorRow;
