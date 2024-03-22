import { ComponentType, SVGProps } from "react";
import { alpha, lighten, styled } from "@mui/material";
import { Link } from "react-router-dom";

export const LinkStyled = styled(Link)(({ theme }) => ({
    color: theme.palette.warning.main,
    "&:hover": {
        color: lighten(theme.palette.warning.main, 0.25),
    },
    "&:focus": {
        color: theme.palette.warning.main,
        textDecoration: "none",
    },
}));

export const styledIcon = (Icon: ComponentType<SVGProps<SVGSVGElement>>) =>
    styled(Icon)(
        ({ theme }) => `
    width: 16px;
    height: 16px;
    color: ${theme.palette.success.main};
    float: left;
    margin-right: 5px;
    margin-top: 2px;
`,
    );

export const TipPanelStyled = styled("div")<{
    isHighlighted: boolean;
}>(({ isHighlighted, theme }) => ({
    height: "75px",
    backgroundColor: isHighlighted ? alpha(theme.palette.error.main, 0.1) : theme.palette.background.paper,
    padding: theme.spacing(1, 1.25),
    fontWeight: "lighter",
    fontSize: "14px",
    color: isHighlighted && theme.palette.text.primary,
    ...(isHighlighted && {
        outline: `2px solid ${theme.palette.error.main}`,
        outlineOffset: "-2px",
    }),
}));
