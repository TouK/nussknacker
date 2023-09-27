import { ComponentType, SVGProps } from "react";
import { styled } from "@mui/material";
import { variables } from "../../stylesheets/variables";
import { Link } from "react-router-dom";
import { alpha } from "../../containers/theme";

export const LinkStyled = styled(Link)`
    color: ${variables.warningColor};
    font-weight: 600;
    white-space: normal !important;
    &:hover {
        color: lighten(${variables.warningColor}, 25%);
    }
    &:focus {
        color: ${variables.warningColor}, text-decoration none;
    }
`;

export const styledIcon = (Icon: ComponentType<SVGProps<SVGSVGElement>>) => styled(Icon)`
    width: 16px;
    height: 16px;
    color: ${variables.okColor};
    float: left;
    margin-right: 5px;
    margin-top: 2px;
`;

export const TipPanelStyled = styled("div")((props: { isHighlighted: boolean }) => ({
    height: "75px",
    backgroundColor: props.isHighlighted ? alpha(variables.errorColor, 0.1) : variables.panelBackground,
    padding: "8px 1px 8px 10px",
    fontWeight: "lighter",
    fontSize: "14px",
    color: props.isHighlighted && variables.defaultTextColor,
    ...(props.isHighlighted && {
        outline: `2px solid ${variables.errorColor}`,
        outlineOffset: "-2px",
    }),
}));
