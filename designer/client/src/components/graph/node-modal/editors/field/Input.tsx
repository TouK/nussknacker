import React from "react";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { InputWithFocusProps, NodeInput } from "../../../../FormElements";
import { cx } from "@emotion/css";
import { FieldError } from "../Validators";
import { isEmpty } from "lodash";

export interface InputProps
    extends Pick<
        InputWithFocusProps,
        "className" | "placeholder" | "autoFocus" | "onChange" | "readOnly" | "type" | "onFocus" | "disabled"
    > {
    value: string;
    inputClassName?: string;
    fieldErrors: FieldError[];
    isMarked?: boolean;
    showValidation?: boolean;
}

export default function Input(props: InputProps): JSX.Element {
    const {
        isMarked,
        showValidation,
        className,
        value,
        fieldErrors,
        type = "text",
        inputClassName,
        autoFocus,
        readOnly,
        placeholder,
        onFocus,
        onChange,
    } = props;

    return (
        <div className={className}>
            <div className={isMarked ? " marked" : ""}>
                {
                    <NodeInput
                        autoFocus={autoFocus}
                        readOnly={readOnly}
                        placeholder={placeholder}
                        onChange={onChange}
                        onFocus={onFocus}
                        type={type}
                        className={cx([
                            !showValidation || isEmpty(fieldErrors) ? "node-input" : "node-input node-input-with-error",
                            inputClassName,
                        ])}
                        value={value || ""}
                    />
                }
            </div>
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
        </div>
    );
}
