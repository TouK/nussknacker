import type { PropsWithChildren } from "react";
import React from "react";

import { nodeValue } from "../../NodeDetailsContent/NodeTableStyled";
import { FormControl } from "../FormControl";
import type { InputProps } from "./Input";
import Input from "./Input";

export type LabeledInputProps = PropsWithChildren<
    Pick<InputProps, "placeholder" | "isMarked" | "readOnly" | "value" | "autoFocus" | "showValidation" | "fieldErrors" | "onChange">
>;

export default function LabeledInput({ children, ...props }: LabeledInputProps): React.JSX.Element {
    return (
        <FormControl>
            {children}
            <Input {...props} className={nodeValue} />
        </FormControl>
    );
}
