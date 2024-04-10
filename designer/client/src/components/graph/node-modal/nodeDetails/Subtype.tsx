import { styled, Typography } from "@mui/material";
import React, { PropsWithChildren } from "react";
import { MODAL_HEADER_HEIGHT } from "../../../../stylesheets/variables";

const SubtypeStyled = styled("div")`
    height: ${MODAL_HEADER_HEIGHT}px;
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
