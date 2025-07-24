import { Box, styled } from "@mui/material";

export const Overlay = styled(Box)({
    pointerEvents: "none",
    overflow: "hidden",
    "& > *": {
        zIndex: 0,
        pointerEvents: "auto",
    },
});
