import { Theme } from "@mui/material";
import { CSSProperties } from "react";

export const buttonBaseStyle = (theme: Theme): CSSProperties | Record<string, unknown> => ({
    ...theme.typography.button,
    textTransform: "capitalize",
    borderRadius: 0,
    backgroundColor: `${theme.palette.background.paper}`,
    transition: `${theme.palette.background.paper} 0.2s`,
    userSelect: "none",
    "&:disabled,&.disabled": {
        opacity: 0.3,
        cursor: "not-allowed !important",
    },
});
