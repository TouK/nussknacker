/* eslint-disable i18next/no-literal-string */
import { useTheme } from "@mui/material";
import Color from "color";
import { css } from "@emotion/css";

export function tint(base: string, amount = 0): string {
    return Color(base).mix(Color("white"), amount).hsl().string();
}

export function alpha(base: string, amount = 1): string {
    return Color(base).alpha(amount).hsl().string();
}

export function tintPrimary(base: string): {
    primary75: string;
    primary25: string;
    primary50: string;
    primary: string;
} {
    return {
        primary: tint(base, 0),
        primary75: tint(base, 0.75),
        primary50: tint(base, 0.5),
        primary25: tint(base, 0.25),
    };
}

export function getContrastColor(color: string, contrast = 5, ratio = 0.1) {
    const baseColor = Color(color);
    const resultColor = baseColor.isDark() ? baseColor.lighten(ratio) : baseColor.darken(ratio);
    if (baseColor.contrast(resultColor) < contrast && resultColor.lightness() % 100 !== 0) {
        return getContrastColor(color, contrast, ratio * 1.1);
    }
    return resultColor.hex();
}

export function getDarkenContrastColor(color: string, contrast = 5, ratio = 0.1) {
    const baseColor = Color(color);
    const resultColor = baseColor.darken(ratio);
    if (baseColor.contrast(resultColor) < contrast && resultColor.lightness() > 0) {
        return getDarkenContrastColor(color, contrast, ratio * 1.1);
    }
    return resultColor.hex();
}

export const useFocus = () => {
    const theme = useTheme();

    return css({
        ":focus, :active:focus": {
            outline: "none",
            borderColor: theme.custom.colors.focusColor,
            boxShadow: `0 0 0 1px ${theme.custom.colors.focusColor}`,
        },
    });
};
