import { lighten, styled } from "@mui/material";

export const StyledBlueButton = styled("button")(
    ({ theme }) => `
    margin: 45px 50px;
    border: none;
    width: 360px;
    min-width: 300px;
    height: ${theme.custom.spacing.controlHeight};
    background-color: ${theme.custom.colors.cerulean};
    color: ${theme.palette.success.contrastText};
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: "Open Sans";
    font-size: 21px;
    font-weight: 600;
    border-radius: 0;
    &:hover {
        color: ${theme.palette.success.contrastText};
        background: ${lighten(theme.custom.colors.cerulean, 0.25)};
    }
    &:focus {
        color: ${theme.palette.success.contrastText};
        #add-icon {
            width: 20px;
            margin-left: 20px;
            &:hover {
                background: ${theme.custom.colors.info};
            }
        }
    }
`,
);
