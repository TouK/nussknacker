import { css, styled, Theme } from "@mui/material";
import { Button } from "../FormElements";

const buttonBase = (theme: Theme) =>
    css({
        borderRadius: 0,
        backgroundColor: theme.palette.background.paper,
        transition: "background-color 0.2s",
        userSelect: "none",
        "&:focus": {
            border: `1px solid ${theme.palette.primary.main}`,
        },
        "&:disabled, &.disabled": {
            opacity: 0.3,
            cursor: "not-allowed !important",
        },
        "&:not(:disabled):hover, &:not(.disabled):hover": {
            backgroundColor: theme.palette.action.hover,
        },
    });

export const StyledButton = styled(Button)(({ theme }) =>
    css([
        buttonBase(theme),
        {
            width: 35,
            height: 35,
            fontWeight: "bold",
            fontSize: 20,
        },
    ]),
);
