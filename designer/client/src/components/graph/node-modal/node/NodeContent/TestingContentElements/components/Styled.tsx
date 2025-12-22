import { Stack, styled } from "@mui/material";

export const StyledStack = styled(Stack)(({ theme }) => ({
    gap: theme.spacing(2),
    padding: theme.spacing(3),
}));
