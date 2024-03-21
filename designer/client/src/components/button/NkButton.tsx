import React from "react";
import { ButtonProps, ButtonWithFocus } from "../withFocus";
import { styled } from "@mui/material";
import { buttonBaseStyle } from "./ButtonBaseStyle";

const NkButtonStyled = styled(ButtonWithFocus)(({ theme }) => ({
    ...buttonBaseStyle(theme),
    width: "180px",
    height: "44px",
    fontWeight: 600,
    fontSize: "18px",
}));

export function NkButton({ ...props }: ButtonProps) {
    return <NkButtonStyled {...props} />;
}
