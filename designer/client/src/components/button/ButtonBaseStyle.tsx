import { variables } from "../../stylesheets/variables";
import { css, Theme } from "@mui/material";

export const buttonBaseStyle = (theme: Theme) =>
    css({
        border: `1px solid ${variables.buttonBorderColor}`,
        borderRadius: 0,
        backgroundColor: `${variables.buttonBkgColor}`,
        color: `${theme.custom.colors.secondaryColor}`,
        transition: `${variables.buttonBkgColor} 0.2s`,
        userSelect: "none",
        "&:disabled,&.disabled": {
            opacity: 0.3,
            cursor: "not-allowed !important",
        },
        "&:not(:disabled):hover,&:not(.disabled):hover": {
            backgroundColor: `${variables.buttonBkgHover}`,
        },
    });
