import { css, Theme } from "@mui/material";

export const buttonBaseStyle = (theme: Theme) =>
    css({
        border: `1px solid ${theme.custom.colors.doveGray}`,
        borderRadius: 0,
        backgroundColor: `${theme.palette.background.paper}`,
        color: `${theme.custom.colors.secondaryColor}`,
        transition: `${theme.palette.background.paper} 0.2s`,
        userSelect: "none",
        "&:disabled,&.disabled": {
            opacity: 0.3,
            cursor: "not-allowed !important",
        },
        "&:not(:disabled):hover,&:not(.disabled):hover": {
            backgroundColor: `${theme.custom.colors.doveGray}`,
        },
    });
