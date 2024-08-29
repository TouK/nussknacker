import { Box, styled } from "@mui/material";
import { getLuminance } from "@mui/system/colorManipulator";
import { blendDarken, blendLighten } from "../../../../containers/theme/helpers";
import PlaceholderIcon from "../images/placeholder-icon.svg";
import React from "react";

const PlaceholderIconWrapper = styled(Box)(({ theme }) => ({
    padding: theme.spacing(1),
    display: "flex",
    justifyContent: "center",
    alignItems: "center",
    backgroundColor:
        getLuminance(theme.palette.background.paper) > 0.5
            ? blendLighten(theme.palette.background.paper, 0.2)
            : blendDarken(theme.palette.background.paper, 0.2),
}));

export const PlaceholderIconFallbackComponent = () => (
    <PlaceholderIconWrapper>
        <PlaceholderIcon />
    </PlaceholderIconWrapper>
);
