import { css, Theme } from "@mui/material";

export const buttonBaseStyle = (theme: Theme) =>
    css({
        border: `1px solid ${theme.custom.colors.tundora}`,
        borderRadius: 0,
        backgroundColor: `${theme.custom.colors.primaryBackground}`,
        color: `${theme.custom.colors.secondaryColor}`,
        transition: `${theme.custom.colors.primaryBackground} 0.2s`,
        userSelect: "none",
        "&:disabled,&.disabled": {
            opacity: 0.3,
            cursor: "not-allowed !important",
        },
        "&:not(:disabled):hover,&:not(.disabled):hover": {
            backgroundColor: `${theme.custom.colors.tundora}`,
        },
    });
