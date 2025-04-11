import { styled } from "@mui/material";

export const Button = styled("button")(({ theme }) => ({
    display: "flex",
    alignItems: "center",
    justifyContent: "start",
    border: "3px solid transparent",
    userSelect: "none",
    height: "fit-content",
    outline: "none",
    backgroundColor: "transparent",
    padding: "4px 0",
    flexDirection: "column",
    color: theme.palette.text.secondary,
}));
