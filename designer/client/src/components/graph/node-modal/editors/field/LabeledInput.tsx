import React, { PropsWithChildren } from "react";
import Input, { InputProps } from "./Input";
import { FormControl } from "@mui/material";
import { nodeValue } from "../../NodeDetailsContent/NodeTableStyled";

export type LabeledInputProps = PropsWithChildren<
    Pick<InputProps, "placeholder" | "isMarked" | "readOnly" | "value" | "autoFocus" | "showValidation" | "fieldErrors" | "onChange">
>;

export default function LabeledInput({ children, ...props }: LabeledInputProps): JSX.Element {
    return (
        <FormControl>
            {children}
            <Input {...props} className={nodeValue} />
        </FormControl>
    );
}
