import React from "react";
import { NodeInput } from "../../../../FormElements";
import { LabeledInputProps } from "./LabeledInput";
import { FormControl } from "@mui/material";
import { nodeValue } from "../../NodeDetailsContent/NodeTableStyled";

export interface CheckboxProps extends Pick<LabeledInputProps, "children" | "autoFocus" | "isMarked" | "onChange" | "readOnly"> {
    value?: boolean;
    checked?: boolean;
}

export default function Checkbox(props: CheckboxProps): JSX.Element {
    const { children, autoFocus, isMarked, value, onChange, readOnly } = props;

    return (
        <FormControl>
            {children}
            <div className={`${nodeValue}${isMarked ? " marked" : ""}${readOnly ? " read-only " : ""}`}>
                <NodeInput autoFocus={autoFocus} type="checkbox" checked={!!value} onChange={onChange} disabled={readOnly} />
            </div>
        </FormControl>
    );
}
