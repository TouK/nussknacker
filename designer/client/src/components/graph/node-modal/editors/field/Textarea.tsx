import { cx } from "@emotion/css";
import ValidationLabels from "../../../../modals/ValidationLabels";
import React, { ChangeEvent } from "react";
import { TextAreaWithFocus } from "../../../../withFocus";
import { FieldError } from "../Validators";

interface Props {
    isMarked: boolean;
    value: string | number;
    readOnly: boolean;
    autoFocus: boolean;
    showValidation: boolean;
    onChange: (event: ChangeEvent<HTMLTextAreaElement>) => void;
    placeholder: string;
    formattedValue: string;
    className: string;
    type: string;
    inputClassName: string;
    onFocus: () => void;
    fieldError: FieldError;
}
export function Textarea(props: Props) {
    const { isMarked, showValidation, className, placeholder, autoFocus, onChange, value, readOnly, inputClassName, onFocus, fieldError } =
        props;

    return (
        <div className={className}>
            <div className={isMarked ? " marked" : ""}>
                {
                    <TextAreaWithFocus
                        autoFocus={autoFocus}
                        readOnly={readOnly}
                        placeholder={placeholder}
                        className={cx([!showValidation || !fieldError ? "node-input" : "node-input node-input-with-error", inputClassName])}
                        value={value || ""}
                        onChange={onChange}
                        onFocus={onFocus}
                    />
                }
            </div>
            {showValidation && <ValidationLabels fieldError={fieldError} />}
        </div>
    );
}
