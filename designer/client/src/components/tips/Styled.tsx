import { ComponentType, SVGProps } from "react";
import { lighten, styled } from "@mui/material";
import { Link } from "react-router-dom";
import { alpha } from "../../containers/theme/helpers";

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
    backgroundColor: isHighlighted ? alpha(theme.custom.colors.error, 0.1) : theme.custom.colors.primaryBackground,
    padding: "8px 10px 8px 10px",
    fontWeight: "lighter",
    fontSize: "14px",
    color: isHighlighted && theme.custom.colors.secondaryColor,
    ...(isHighlighted && {
        outline: `2px solid ${theme.custom.colors.error}`,
        outlineOffset: "-2px",
    }),
}));
