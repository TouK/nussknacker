import { Box, styled } from "@mui/material";

export const Overlay = styled(Box)({
    pointerEvents: "none",
    "& > *": {
        zIndex: 0,
        pointerEvents: "auto",
    },
});
