import React from "react";
import { styled, Typography } from "@mui/material";
import { PropsWithChildren } from "react";
import { variables } from "../../../../stylesheets/variables";
import { blendLighten } from "../../../../containers/theme/nuTheme";

const SubtypeStyled = styled("div")(({ theme }) => ({
    height: `${variables.modalHeaderHeight}px`,
    backgroundColor: blendLighten(theme.palette.background.paper, 0.1),
    display: "flex",
    alignItems: "center",
    padding: theme.spacing(0, 1.25),
}));

export const Subtype = ({ children }: PropsWithChildren<unknown>) => {
    return (
        <SubtypeStyled>
            <Typography mx={0.5} variant={"subtitle2"}>
                {children}
            </Typography>
        </SubtypeStyled>
    );
};
