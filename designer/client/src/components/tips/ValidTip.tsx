import React, { ComponentType, SVGProps } from "react";
import { styledIcon } from "./Styled";
import { Box, Typography } from "@mui/material";

export default function ValidTip({ icon: Icon, message }: { icon: ComponentType<SVGProps<SVGSVGElement>>; message: string }) {
    const StyledIcon = styledIcon(Icon);
    return (
        <Box mb={0.5}>
            <StyledIcon />
            <Typography variant={"body2"}>{message}</Typography>
        </Box>
    );
}
