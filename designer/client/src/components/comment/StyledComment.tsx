import { styled } from "@mui/material";

export const PanelComment = styled("div")(({ theme }) => ({
    marginTop: "1px",
    fontSize: "12px",
    wordBreak: "break-word",
    a: {
        color: theme.palette.primary.main,
    },
}));
