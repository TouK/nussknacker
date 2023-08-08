import classNames from "classnames";
import ValidationLabels from "../../../../modals/ValidationLabels";
import React, { ChangeEvent, FC } from "react";
import { TextAreaWithFocus } from "../../../../withFocus";
import { isEmpty } from "lodash";
import { NodeValidationError } from "../../../../../types";

interface Props {
    isMarked: boolean;
    value: string;
    readOnly: boolean;
    autoFocus: boolean;
    showValidation: boolean;
    fieldErrors: NodeValidationError[];
    onChange: (e: ChangeEvent<{ value: string }>) => void;
    placeholder: string;
    formattedValue: string;
    className: string;
    type: string;
    inputClassName: string;
    onFocus: () => void;
}

export const Textarea: FC<Props> = ({
    isMarked,
    showValidation,
    className,
    placeholder,
    autoFocus,
    onChange,
    value,
    fieldErrors,
    readOnly,
    inputClassName,
    onFocus,
}) => {
    return (
        <div className={className}>
            <div className={isMarked ? " marked" : ""}>
                {
                    <TextAreaWithFocus
                        autoFocus={autoFocus}
                        readOnly={readOnly}
                        placeholder={placeholder}
                        className={classNames([
                            !showValidation || isEmpty(fieldErrors) ? "node-input" : "node-input node-input-with-error",
                            inputClassName,
                        ])}
                        value={value || ""}
                        onChange={onChange}
                        onFocus={onFocus}
                    />
                }
            </div>
            {showValidation && <ValidationLabels fieldErrors={[]} />}
        </div>
    );
};
