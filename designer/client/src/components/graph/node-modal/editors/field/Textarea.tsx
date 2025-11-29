import { cx } from "@emotion/css";
import { isEmpty } from "lodash";
import type { ChangeEvent } from "react";
import React from "react";

import { TextArea } from "../../../../FormElements";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { nodeInput, nodeInputWithError } from "../../NodeDetailsContent/NodeTableStyled";
import type { FieldError } from "../Validators";

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
    inputClassName: string;
    onFocus: () => void;
    fieldErrors: FieldError[];
}
export function Textarea(props: Props) {
    const { isMarked, showValidation, className, placeholder, autoFocus, onChange, value, readOnly, inputClassName, onFocus, fieldErrors } =
        props;

    return (
        <div className={className}>
            <div className={isMarked ? " marked" : ""}>
                <TextArea
                    autoFocus={autoFocus}
                    readOnly={readOnly}
                    placeholder={placeholder}
                    className={cx([
                        !showValidation || isEmpty(fieldErrors) ? nodeInput : `${nodeInput} ${nodeInputWithError}`,
                        inputClassName,
                    ])}
                    value={value || ""}
                    onChange={onChange}
                    onFocus={onFocus}
                />
            </div>
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
        </div>
    );
}
