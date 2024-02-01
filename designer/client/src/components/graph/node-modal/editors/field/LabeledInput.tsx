import React, { PropsWithChildren } from "react";
import Input, { InputProps } from "./Input";
import { FormControl } from "@mui/material";

export type LabeledInputProps = PropsWithChildren<
    Pick<InputProps, "placeholder" | "isMarked" | "readOnly" | "value" | "autoFocus" | "showValidation" | "fieldErrors" | "onChange">
>;

export default function LabeledInput({ children, ...props }: LabeledInputProps): JSX.Element {
    return (
        <FormControl>
            {children}
            <Input {...props} className={"node-value"} />
        </FormControl>
    );
}
