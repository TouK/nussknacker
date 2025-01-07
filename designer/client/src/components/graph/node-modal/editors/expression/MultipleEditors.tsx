import React, { ReactNode, useState } from "react";
import { editors } from "./Editor";
import { ExpressionObj } from "./types";
import { Option, TypeSelect } from "../../fragment-input-definition/TypeSelect";
import { ParamType } from "../types";
import { FieldError, PossibleValue } from "../Validators";
import { VariableTypes } from "../../../../../types";

interface Props {
    expressionObj: ExpressionObj;
    fieldLabel?: string;
    readOnly?: boolean;
    valueClassName?: string;
    param?: ParamType;
    values?: Array<PossibleValue>;
    isMarked?: boolean;
    showValidation?: boolean;
    onValueChange: (value: string | ExpressionObj) => void;
    fieldErrors: FieldError[];
    variableTypes: VariableTypes;
    validationLabelInfo?: ReactNode;
    placeholder?: string;
}

export const MultipleEditors = (props: Props) => {
    const { param, onValueChange } = props;

    const [selectedEditor, setSelectedEditor] = useState(
        param.editors.filter((editor) => editor.language === props.expressionObj.language)?.[0] ?? param.editors[0],
    );
    const Editor = editors[selectedEditor.type];
    const availableEditorsOptions: Option[] = param.editors.map((editor) => ({
        label: editor.type,
        value: editor.type,
        isDisabled: false,
    }));
    const availableEditorsOption = availableEditorsOptions.find(({ value }) => value === selectedEditor.type) || availableEditorsOptions[0];
    return (
        <>
            <Editor {...props} rows={1} />
            <TypeSelect
                onChange={(value) => {
                    const selectedEditor = param.editors.find((editor) => editor.type === value);
                    onValueChange({ ...props.expressionObj, language: selectedEditor.language });
                    setSelectedEditor(selectedEditor);
                }}
                value={availableEditorsOption}
                options={availableEditorsOptions}
            />
        </>
    );
};
