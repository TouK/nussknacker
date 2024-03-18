import { css, cx } from "@emotion/css";
import React, { forwardRef, Ref } from "react";
import { ValueFieldProps } from "../valueField";
import { NodeInput } from "../withFocus";
import { alpha, useTheme } from "@mui/material";
import { blendLighten } from "../../containers/theme/nuTheme";

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
        color: theme.palette.text.secondary,
        borderColor: theme.custom.colors.borderColor,
        backgroundColor: theme.palette.background.paper,
        fontFamily: "inherit",
        "::placeholder": {
            color: alpha(theme.palette.text.secondary, 0.25),
            opacity: 1 /* Firefox */,
            fontFamily: "inherit",
        },
        "::-ms-input-placeholder": {
            /* Edge 12 -18 */ color: alpha(theme.palette.text.secondary, 0.25),
        },
    });

    return (
        <NodeInput
            ref={ref}
            sx={(theme) => ({ background: blendLighten(theme.palette.background.paper, 0.04) })}
            type="text"
            placeholder={placeholder}
            className={cx(styles, className)}
            value={value || ""}
            onChange={(e) => onChange(e.target.value)}
        />
    );
});
