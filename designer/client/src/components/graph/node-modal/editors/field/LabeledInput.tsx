import React, { PropsWithChildren } from "react";
import Input, { InputProps } from "./Input";
import { NodeRow } from "../../NodeDetailsContent/NodeStyled";

export type LabeledInputProps = PropsWithChildren<
    Pick<InputProps, "placeholder" | "isMarked" | "readOnly" | "value" | "autoFocus" | "showValidation" | "fieldError" | "onChange">
>;

export default function LabeledInput({ children, ...props }: LabeledInputProps): JSX.Element {
    return (
        <NodeRow>
            {children}
            <Input {...props} className={"node-value"} />
        </NodeRow>
    );
}
