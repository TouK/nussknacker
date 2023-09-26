import { css } from "@mui/material";
import { variables } from "../stylesheets/variables";

export const NodeInputCss = css`
    height: 35px;
    width: 100%;
    padding: 0 10px;
    border: none;
    background-color: ${variables.commentBkgColor} !important;
    color: ${variables.defaultTextColor} !important;
    font-weight: 400;
    font-size: 14px;
    outline: 1px solid rgba(255, 255, 255, 0.075) !important;
    &:-moz-disabled {
        background-color: ${variables.panelBkgColor};
    }
    &:disabled {
        background-color: ${variables.panelBkgColor};
    }
    &[type="checkbox"] {
        height: 20px;
    }
`;
