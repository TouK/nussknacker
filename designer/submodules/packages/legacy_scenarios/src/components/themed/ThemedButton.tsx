import { css, cx } from "@emotion/css";
import Color from "color";
import React from "react";
import { useNkTheme } from "../../containers/theme";
import { bootstrapStyles } from "../../styles";
import { ButtonProps, ButtonWithFocus } from "../withFocus";

export const ThemedButton = ({ className, ...props }: ButtonProps) => {
    const { theme } = useNkTheme();
    const styles = css({
        "&&": {
            height: theme?.spacing?.controlHeight,
            borderRadius: theme?.borderRadius,

            backgroundColor: theme.colors.accent,
            "&:hover, &:focus, &:active": {
                backgroundColor: Color(theme.colors.accent).darken(0.2).string(),
            },
            "&, &:hover, &:active": {
                borderColor: Color(theme.colors.accent).darken(0.1).string(),
            },
            "&:focus": {
                "&:hover, &:active": {
                    borderColor: theme.colors.focusColor,
                    backgroundColor: Color(theme.colors.accent).darken(0.3).string(),
                },
            },
        },
    });
    return <ButtonWithFocus {...props} className={cx(bootstrapStyles.btn, bootstrapStyles.btnPrimary, styles, className)} />;
};
