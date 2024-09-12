import { Button, styled } from "@mui/material";

export const ToggleItemsButton = styled(Button)(({ theme }) => ({
    textTransform: "lowercase",
    fontSize: theme.typography.caption.fontSize,
    fontWeight: theme.typography.caption.fontWeight,
}));

export const ToggleItemsRoot = styled("div")(({ theme }) => ({
    display: "flex",
    alignItems: "center",
    justifyContent: "flex-end",
}));
