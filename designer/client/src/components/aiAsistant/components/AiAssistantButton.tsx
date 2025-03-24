import React, { useState } from "react";
import { Box, Divider, Paper, Typography } from "@mui/material";
import { AiAssistant } from "./AiAssistant";
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
                right={320}
                position={"fixed"}
                zIndex={1800}
                p={2}
                onClick={handleClick}
                sx={(theme) => ({
                    background: blendDarken(theme.palette.primary.main, 0.6),
                    cursor: "pointer",
                    width: 75,
                    height: 75,
                    borderRadius: "50%",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                })}
            >
                <NuIcon />
                <Typography component="span" variant={"overline"} fontWeight={"bold"} pt={0.5}>
                    Assistant
                </Typography>
            </Box>
            {open && <AiAssistant />}
        </div>
    );
};
