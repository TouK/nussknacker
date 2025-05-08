import { styled } from "@mui/material";

export const Button = styled("button")(({ theme }) => ({
    "--margin": "2px",
    margin: "calc(var(--margin))",
    borderRadius: "calc(3 * var(--margin))",
    display: "flex",
    alignItems: "center",
    justifyContent: "start",
    border: "3px solid transparent",
    userSelect: "none",
    height: "fit-content",
    outline: "none",
    backgroundColor: "transparent",
    padding: 0,
    flexDirection: "column",
    color: "inherit",
    "&[disabled]": {
        opacity: 0.3,
    },
}));
