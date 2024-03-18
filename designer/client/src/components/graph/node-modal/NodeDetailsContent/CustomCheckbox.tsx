import { css, Theme } from "@mui/material";
import { blendLighten } from "../../../../containers/theme/nuTheme";

export const customCheckbox = (checkboxWidth: string, theme: Theme) => css`
    input[type="checkbox"] {
        text-rendering: optimizeSpeed;
        width: ${checkboxWidth};
        height: ${checkboxWidth};
        margin: 0;
        margin-right: 1px;
        display: block;
        position: relative;
        cursor: pointer;
        -moz-appearance: none;
        border: none;
    }

    input[type="checkbox"]:after {
        content: "";
        vertical-align: middle;
        text-align: center;
        line-height: ${checkboxWidth};
        position: absolute;
        cursor: pointer;
        height: ${checkboxWidth};
        width: ${checkboxWidth};
        font-size: calc(${checkboxWidth} - 6);
        background: ${theme.palette.background.paper};
    }
    input[type="checkbox"]:checked::after {
        content: "\\2713";
        font-size: 1rem;
    }

    input[type="checkbox"]:disabled {
        opacity: 0.3;
        cursor: auto;
    }

    input[type="checkbox"]:focus {
        border: none;
        &:after {
            outline: 1px solid ${theme.palette.primary.main};
        }
    }
`;
