import React, { useState } from "react";
import { Box, Paper } from "@mui/material";
import { AiAssistant } from "../AiAssistant";
import NuIcon from "../../../assets/img/nussknacker-logo-icon.svg";
import { blendDarken } from "../../../containers/theme/helpers";

export const AiAssistantButton = () => {
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
    const open = Boolean(anchorEl);

    const handleClick = (event: React.MouseEvent<HTMLElement>) => {
        if (anchorEl) {
            setAnchorEl(null);
        } else {
            setAnchorEl(event.currentTarget);
        }
    };

    return (
        <div>
            <Box
                bottom={32}
                right={32}
                position={"fixed"}
                zIndex={1800}
                p={2}
                onClick={handleClick}
                sx={(theme) => ({
                    background: blendDarken(theme.palette.primary.main, 0.6),
                    cursor: "pointer",
                    width: 100,
                    height: 100,
                    borderRadius: "50%",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                })}
            >
                <NuIcon />
            </Box>
            {open && (
                <Box position={"fixed"} bottom={135} right={40} zIndex={1800} sx={{ background: "white" }}>
                    <Paper sx={{ height: 500, width: 500, p: 2 }}>
                        <AiAssistant />
                    </Paper>
                </Box>
            )}
        </div>
    );
};
