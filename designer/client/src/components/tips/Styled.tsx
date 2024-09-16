import { ComponentType, SVGProps } from "react";
import { alpha, lighten, styled } from "@mui/material";
import { Link } from "react-router-dom";
import { SvgIconOwnProps } from "@mui/material/SvgIcon/SvgIcon";

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

export const styledIcon = (Icon: ComponentType<SVGProps<SVGSVGElement>>, color: string) =>
    styled(Icon)(() => ({
        width: "16px",
        height: "16px",
        float: "left",
        marginRight: "5px",
        marginTop: "2px",
        color,
    }));

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

export const SearchPanelStyled = styled("div")(({ theme }) => ({
    width: "100%",
    padding: theme.spacing(1, 1.25),
}));
