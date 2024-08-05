import React, { PropsWithChildren } from "react";
import Checkbox from "./Checkbox";
import Input from "./Input";
import LabeledInput from "./LabeledInput";
import LabeledTextarea from "./LabeledTextarea";
import UnknownField from "./UnknownField";
import { FieldError } from "../Validators";
import { MarkdownFormControl } from "./MarkdownFormControl";

export enum FieldType {
    input = "input",
    unlabeledInput = "unlabeled-input",
    checkbox = "checkbox",
    plainTextarea = "plain-textarea",
    markdown = "markdown",
}

export interface FieldProps {
    isMarked: boolean;
    readOnly: boolean;
    showValidation: boolean;
    autoFocus: boolean;
    className: string;
    fieldErrors: FieldError[];
    type: FieldType;
    value: string | boolean;
    onChange: (value: string | boolean) => void;
    description?: string;
}

export default function Field({ type, children, ...props }: PropsWithChildren<FieldProps>): JSX.Element {
    switch (type) {
        case FieldType.input:
            return (
                <LabeledInput {...props} value={props.value?.toString() || ""} onChange={({ target }) => props.onChange(target.value)}>
                    {children}
                </LabeledInput>
            );
        case FieldType.unlabeledInput:
            return <Input {...props} value={props.value?.toString() || ""} onChange={({ target }) => props.onChange(target.value)} />;
        case FieldType.checkbox:
            return (
                <Checkbox {...props} value={!!props.value} onChange={({ target }) => props.onChange(target.checked)}>
                    {children}
                </Checkbox>
            );
        case FieldType.plainTextarea:
            return (
                <LabeledTextarea {...props} value={props.value?.toString() || ""} onChange={({ target }) => props.onChange(target.value)}>
                    {children}
                </LabeledTextarea>
            );
        case FieldType.markdown:
            return (
                <MarkdownFormControl {...props} value={props.value?.toString() || ""} onChange={(value) => props.onChange(value)}>
                    {children}
                </MarkdownFormControl>
            );
        default:
            return <UnknownField />;
    }
}
