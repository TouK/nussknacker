import React from "react";
import { styled, Typography } from "@mui/material";
import { PropsWithChildren } from "react";
import { variables } from "../../../../stylesheets/variables";

const SubtypeStyled = styled("div")`
    height: ${variables.modalHeaderHeight}px;
    background: #3a3a3a;
    display: flex;
    align-items: center;
    padding: 0 10px;
`;

export const Subtype = ({ children }: PropsWithChildren<unknown>) => {
    return (
        <SubtypeStyled>
            <Typography mx={0.5} variant={"subtitle2"}>
                {children}
            </Typography>
        </SubtypeStyled>
    );
};
