import { css, cx } from "@emotion/css";
import React, { forwardRef, Ref } from "react";
import { ValueFieldProps } from "../valueField";
import { NodeInput } from "../withFocus";
import { lighten, useTheme } from "@mui/material";

export type InputProps = ValueFieldProps<string> & {
    placeholder?: string;
    className?: string;
};

export const ThemedInput = forwardRef(function ThemedInput(
    { value, onChange, placeholder, className }: InputProps,
    ref: Ref<HTMLInputElement>,
): JSX.Element {
    const theme = useTheme();
    const styles = css({
        height: theme.custom.spacing.controlHeight,
        borderRadius: theme.custom.borderRadius,
        color: theme.custom.colors?.primaryColor,
        borderColor: theme.custom.colors.borderColor,
        backgroundColor: theme.palette.background.paper,
        fontFamily: "inherit",
        "::placeholder": {
            color: theme.custom.colors.dustyGray,
            opacity: 1 /* Firefox */,
            fontFamily: "inherit",
        },
        "::-ms-input-placeholder": {
            /* Edge 12 -18 */ color: theme.custom.colors.dustyGray,
        },
    });

    return (
        <NodeInput
            ref={ref}
            sx={(theme) => ({ background: lighten(theme.palette.background.paper, 0.1) })}
            type="text"
            placeholder={placeholder}
            className={cx(styles, className)}
            value={value || ""}
            onChange={(e) => onChange(`${e.target.value}`)}
        />
    );
});
