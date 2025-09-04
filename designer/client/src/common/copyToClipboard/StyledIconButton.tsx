import { IconButton, styled } from "@mui/material";

export const StyledIconButton = styled(IconButton)(({ theme }) => ({
    color: theme.palette.common.white,
    padding: theme.spacing(0.5),
    "&:hover": {
        backgroundColor: theme.palette.action.hover,
    },
}));
