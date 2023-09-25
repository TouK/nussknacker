import React from "react";
import { css, styled } from "@mui/material";
import { PropsWithChildren } from "react";
import { variables } from "../../../../stylesheets/variables";

export const SubTypeHeader = css({
    height: `${variables.modalHeaderHeight}px`,
    background: "#3a3a3a",
    display: "flex",
    alignItems: "center",
    paddingLeft: "10px",
    paddingRight: "10px",
});

const SubtypeStyled = styled("div")`
    ${SubTypeHeader}
`;

export const Subtype = ({ children }: PropsWithChildren<unknown>) => {
    return (
        <SubtypeStyled>
            <span>{children}</span>
        </SubtypeStyled>
    );
};
