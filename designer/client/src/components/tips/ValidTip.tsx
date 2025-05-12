import { Box, Typography } from "@mui/material";
import type { ComponentType, SVGProps } from "react";
import React from "react";

import { styledIcon } from "./Styled";

export default function ValidTip({
    icon: Icon,
    message,
    color,
}: {
    icon: ComponentType<SVGProps<SVGSVGElement>>;
    message: string;
    color: string;
}) {
    const StyledIcon = styledIcon(Icon, color);
    return (
        <Box mb={0.5}>
            <StyledIcon />
            <Typography variant={"body2"}>{message}</Typography>
        </Box>
    );
}
