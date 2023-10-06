import React from "react";
import { ButtonProps, ButtonWithFocus } from "../withFocus";
import { styled } from "@mui/material";
import { buttonBaseStyle } from "./ButtonBaseStyle";

const NkButtonStyled = styled(ButtonWithFocus)(
    ({ theme }) => `
    ${buttonBaseStyle(theme)};
    width: 180px;
    height: 44px;
    font-weight: 600;
    font-size: 18px;
`,
);

export function NkButton({ ...props }: ButtonProps) {
    return <NkButtonStyled {...props} />;
}
