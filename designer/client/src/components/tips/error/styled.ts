import type { TypographyProps } from "@mui/material";
import { styled, Typography } from "@mui/material";
import Color from "color";
import type { NavLink } from "react-router-dom";

export const ErrorLinkStyle = styled(Typography)<TypographyProps<"span" | typeof NavLink>>(({ theme }) => ({
    whiteSpace: "normal",
    fontWeight: 600,
    color: theme.palette.error.light,
    "a&": {
        "&:hover": {
            color: Color(theme.palette.error.main).lighten(0.25).hex(),
        },
        "&:focus": {
            color: theme.palette.error.main,
            textDecoration: "none",
        },
    },
}));
