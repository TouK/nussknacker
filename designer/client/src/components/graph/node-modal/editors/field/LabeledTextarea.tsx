import React from "react";
import { isEmpty } from "lodash";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { TextAreaWithFocus, TextAreaWithFocusProps } from "../../../../withFocus";
import { LabeledInputProps } from "./LabeledInput";
import { NodeRow } from "../../NodeDetailsContent/NodeStyled";

export interface LabeledTextareaProps
    extends Pick<LabeledInputProps, "value" | "isMarked" | "children" | "showValidation" | "validators">,
        Pick<TextAreaWithFocusProps, "className" | "autoFocus" | "onChange" | "readOnly" | "cols" | "rows"> {}

export default function LabeledTextarea(props: LabeledTextareaProps): JSX.Element {
    const { value, className, isMarked, rows = 1, cols = 50, children, showValidation, validators, ...passProps } = props;

    const lineEndPattern = /\r\n|\r|\n/;

    return (
        <NodeRow>
            {children}
            <div className={`node-value${isMarked ? " marked" : ""}`}>
                <TextAreaWithFocus
                    {...passProps}
                    rows={!isEmpty(value) ? value.split(lineEndPattern).length : rows}
                    cols={cols}
                    className={className}
                    value={value}
                />
                {showValidation && <ValidationLabels validators={validators} values={[value]} />}
            </div>
        </NodeRow>
    );
}
