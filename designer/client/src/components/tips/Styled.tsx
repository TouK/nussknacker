import { ComponentType, SVGProps } from "react";
import { lighten, styled, Theme } from "@mui/material";
import { variables } from "../../stylesheets/variables";
import { Link } from "react-router-dom";
import { alpha } from "../../containers/theme/helpers";

export const LinkStyled = styled(Link)`
    color: ${variables.warningColor};
    font-weight: 600;
    white-space: normal !important;
    &:hover {
        color: ${lighten(variables.warningColor, 0.25)};
    }
    &:focus {
        color: ${variables.warningColor};
        text-decoration: none;
    }
`;

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
    backgroundColor: isHighlighted ? alpha(theme.custom.colors.error, 0.1) : variables.panelBackground,
    padding: "8px 10px 8px 10px",
    fontWeight: "lighter",
    fontSize: "14px",
    color: isHighlighted && variables.defaultTextColor,
    ...(isHighlighted && {
        outline: `2px solid ${theme.custom.colors.error}`,
        outlineOffset: "-2px",
    }),
}));
