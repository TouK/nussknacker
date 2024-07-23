import React from "react";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { InputWithFocusProps, NodeInput } from "../../../../FormElements";
import { cx } from "@emotion/css";
import { FieldError } from "../Validators";
import { isEmpty } from "lodash";
import { nodeInput, nodeInputWithError } from "../../NodeDetailsContent/NodeTableStyled";

export interface InputProps
    extends Pick<
        InputWithFocusProps,
        "className" | "placeholder" | "autoFocus" | "onChange" | "onBlur" | "readOnly" | "type" | "onFocus" | "disabled" | "id"
    > {
    value: string;
    inputClassName?: string;
    fieldErrors?: FieldError[];
    isMarked?: boolean;
    showValidation?: boolean;
}

export default function Input(props: InputProps): JSX.Element {
    const {
        isMarked,
        showValidation,
        className,
        value,
        fieldErrors = [],
        type = "text",
        inputClassName,
        autoFocus,
        readOnly,
        placeholder,
        onFocus,
        onChange,
        onBlur,
        ...rest
    } = props;

    return (
        <div className={className}>
            <div className={isMarked ? " marked" : ""}>
                <NodeInput
                    {...rest}
                    autoFocus={autoFocus}
                    readOnly={readOnly}
                    placeholder={placeholder}
                    onChange={onChange}
                    onFocus={onFocus}
                    onBlur={onBlur}
                    type={type}
                    className={cx([
                        !showValidation || isEmpty(fieldErrors) ? nodeInput : `${nodeInput} ${nodeInputWithError}`,
                        inputClassName,
                    ])}
                    value={value || ""}
                />
            </div>
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
        </div>
    );
}
