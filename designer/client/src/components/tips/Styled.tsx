import { ComponentType, SVGProps } from "react";
import { alpha, lighten, styled } from "@mui/material";
import { Link } from "react-router-dom";

export const LinkStyled = styled(Link)(
    ({ theme }) => `
    color: ${theme.custom.colors.warning};
    &:hover {
        color: ${lighten(theme.custom.colors.warning, 0.25)};
    }
    &:focus {
        color: ${theme.custom.colors.warning};
        text-decoration: none;
    }
`,
);

export const styledIcon = (Icon: ComponentType<SVGProps<SVGSVGElement>>) =>
    styled(Icon)(
        ({ theme }) => `
    width: 16px;
    height: 16px;
    color: ${theme.custom.colors.ok};
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
        outline: `2px solid ${theme.custom.colors.error}`,
        outlineOffset: "-2px",
    }),
}));
