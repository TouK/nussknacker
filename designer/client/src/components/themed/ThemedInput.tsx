import { alpha } from "@mui/material";
import type { FocusEventHandler, Ref } from "react";
import React, { forwardRef } from "react";

import { blendLighten } from "../../containers/theme/helpers";
import { NodeInput } from "../FormElements";
import type { ValueFieldProps } from "../valueField";

export type InputProps = ValueFieldProps<string> & {
    placeholder?: string;
    className?: string;
    onFocus?: FocusEventHandler;
    onBlur?: FocusEventHandler;
};

export const ThemedInput = forwardRef(function ThemedInput(
    { value, onChange, placeholder, className, ...props }: InputProps,
    ref: Ref<HTMLInputElement>,
): React.JSX.Element {
    return (
        <NodeInput
            ref={ref}
            sx={(theme) => ({
                ...theme.typography.body1,
                "::placeholder": {
                    color: alpha(theme.palette.text.secondary, 0.25),
                    opacity: 1 /* Firefox */,
                    fontFamily: "inherit",
                },
                "::-ms-input-placeholder": {
                    /* Edge 12 -18 */ color: alpha(theme.palette.text.secondary, 0.25),
                },
                background: blendLighten(theme.palette.background.paper, 0.04),
            })}
            type="text"
            placeholder={placeholder}
            className={className}
            value={value || ""}
            onChange={(e) => {
                onChange(e.target.value);
            }}
            {...props}
        />
    );
});
