import { css, Theme } from "@mui/material";

export const NodeInputCss = (theme: Theme) => css`
    height: 35px;
    width: 100%;
    padding: 0 10px;
    border: none;
    background-color: ${theme.palette.background.paper};
    color: ${theme.custom.colors.secondaryColor};
    font-weight: 400;
    font-size: 14px;

    &:-moz-disabled {
        background-color: ${theme.custom.colors.tundora};
    }
    &:disabled {
        background-color: ${theme.custom.colors.tundora};
    }
    &[type="checkbox"] {
        height: 20px;
    }
`;
