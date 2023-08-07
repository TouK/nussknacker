import classNames from "classnames";
import { allValid, Validator } from "../Validators";
import ValidationLabels from "../../../../modals/ValidationLabels";
import React, { ChangeEvent, FC } from "react";
import { TextAreaWithFocus } from "../../../../withFocus";

interface Props {
    isMarked: boolean;
    value: string;
    readOnly: boolean;
    autoFocus: boolean;
    showValidation: boolean;
    validators: Validator[];
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
    validators,
    readOnly,
    formattedValue,
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
                            !showValidation || allValid(validators, [formattedValue ? formattedValue : value])
                                ? "node-input"
                                : "node-input node-input-with-error",
                            inputClassName,
                        ])}
                        value={value || ""}
                        onChange={onChange}
                        onFocus={onFocus}
                    />
                }
            </div>
            {showValidation && <ValidationLabels validators={validators} values={[formattedValue ? formattedValue : value]} />}
        </div>
    );
};
