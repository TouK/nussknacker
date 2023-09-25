import { variables } from "../../stylesheets/variables";
import { css } from "@mui/material";

export const buttonBaseStyle = css({
    border: `1px solid ${variables.buttonBorderColor}`,
    borderRadius: 0,
    backgroundColor: `${variables.buttonBkgColor}`,
    color: `${variables.buttonTextColor}`,
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
