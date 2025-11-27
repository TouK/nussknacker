import { isEmpty } from "lodash";
import React from "react";

import type { TextAreaWithFocusProps } from "../../../../FormElements";
import { TextArea } from "../../../../FormElements";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { nodeValue } from "../../NodeDetailsContent/NodeTableStyled";
import { FormControl } from "../FormControl";
import type { LabeledInputProps } from "./LabeledInput";

export interface LabeledTextareaProps
    extends Pick<LabeledInputProps, "value" | "isMarked" | "children" | "showValidation" | "fieldErrors">,
        Pick<TextAreaWithFocusProps, "className" | "autoFocus" | "onChange" | "readOnly" | "cols" | "rows"> {}

export default function LabeledTextarea(props: LabeledTextareaProps): React.JSX.Element {
    const { value, className, isMarked, rows = 1, cols = 50, children, showValidation, fieldErrors, ...passProps } = props;

    const lineEndPattern = /\r\n|\r|\n/;

    return (
        <FormControl>
            {children}
            <div className={`${nodeValue}${isMarked ? " marked" : ""}`}>
                <TextArea
                    {...passProps}
                    rows={!isEmpty(value) ? value.split(lineEndPattern).length : rows}
                    cols={cols}
                    className={className}
                    value={value}
                />
                {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
            </div>
        </FormControl>
    );
}
