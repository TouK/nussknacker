import { css, Theme } from "@mui/material";

export const NodeInputCss = (theme: Theme) => css`
    height: 35px;
    width: 100%;
    padding: 0 10px;
    border: none;

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
