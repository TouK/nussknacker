import { cx } from "@emotion/css";
import { isEmpty } from "lodash";
import React from "react";

import type { InputWithFocusProps } from "../../../../FormElements";
import { NodeInput } from "../../../../FormElements";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { nodeInput, nodeInputWithError } from "../../NodeDetailsContent/NodeTableStyled";
import type { FieldError } from "../Validators";

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

export default function Input(props: InputProps): React.JSX.Element {
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
        disabled,
        id,
    } = props;

    return (
        <div className={className}>
            <div className={cx({ marked: isMarked })}>
                <NodeInput
                    id={id}
                    disabled={disabled}
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
