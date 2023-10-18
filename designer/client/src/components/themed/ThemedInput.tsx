import { css, cx } from "@emotion/css";
import React, { forwardRef, Ref } from "react";
import { ValueFieldProps } from "../valueField";
import { NodeInput } from "../withFocus";
import { useTheme } from "@mui/material";

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
        backgroundColor: theme.custom.colors.secondaryBackground,
        fontFamily: "inherit",
        "::placeholder": {
            color: "#999",
            opacity: 1 /* Firefox */,
            fontFamily: "inherit",
        },
        "::-ms-input-placeholder": {
            /* Edge 12 -18 */ color: "#999",
        },
    });

    return (
        <NodeInput
            ref={ref}
            type="text"
            placeholder={placeholder}
            className={cx(styles, className)}
            value={value || ""}
            onChange={(e) => onChange(`${e.target.value}`)}
        />
    );
});
