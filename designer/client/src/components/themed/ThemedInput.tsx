import React, { forwardRef, Ref } from "react";
import { ValueFieldProps } from "../valueField";
import { NodeInput } from "../FormElements";
import { alpha } from "@mui/material";

import { blendLighten } from "../../containers/theme/helpers";

export type InputProps = ValueFieldProps<string> & {
    placeholder?: string;
    className?: string;
};

export const ThemedInput = forwardRef(function ThemedInput(
    { value, onChange, placeholder, className }: InputProps,
    ref: Ref<HTMLInputElement>,
): JSX.Element {
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
            onChange={(e) => onChange(e.target.value)}
        />
    );
});
