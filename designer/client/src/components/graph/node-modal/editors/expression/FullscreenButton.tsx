import { Fullscreen, FullscreenExit } from "@mui/icons-material";
import { IconButton } from "@mui/material";
import React from "react";

import { InfoTooltip } from "../InfoTooltip/InfoTooltip";

interface Props {
    isFullscreen: boolean;
    onToggle: () => void;
}

export const FullscreenButton = ({ isFullscreen, onToggle }: Props) => {
    const Icon = isFullscreen ? FullscreenExit : Fullscreen;
    return (
        <InfoTooltip title={isFullscreen ? "Close" : "Expand"} variant="hover">
            <IconButton onClick={onToggle} size="small" sx={{ padding: 0, color: "inherit", "&:focus": { outline: "none" } }}>
                <Icon fontSize="small" />
            </IconButton>
        </InfoTooltip>
    );
};
