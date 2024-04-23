import { styled } from "@mui/material";

export const Page = styled("div")({
    position: "relative",
    overflow: "hidden",
    height: "100%",
    display: "flex",
    flexDirection: "column",
});

export const GraphPage = styled(Page)(({ theme }) => ({
    backgroundColor: theme.palette.background.default,
    zIndex: 1,
}));
