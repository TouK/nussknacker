import { styled } from "@mui/material";
import React from "react";

const FixedContainer = styled("div")({
    position: "fixed",
    left: 0,
    top: 0,
    zIndex: 9999,
});

export const GlideGridPortal = () => <FixedContainer id="portal" />;
