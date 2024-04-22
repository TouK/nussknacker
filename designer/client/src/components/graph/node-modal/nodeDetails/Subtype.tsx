import { styled, Typography } from "@mui/material";
import React, { PropsWithChildren } from "react";
import { MODAL_HEADER_HEIGHT } from "../../../../stylesheets/variables";

import { blendLighten } from "../../../../containers/theme/helpers";

const SubtypeStyled = styled("div")(({ theme }) => ({
    height: `${MODAL_HEADER_HEIGHT}px`,
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
