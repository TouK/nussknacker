import { Box, Typography } from "@mui/material";
import { useWindowManager } from "@touk/window-manager";
import React, { useCallback } from "react";

import NuIcon from "../../../assets/img/nussknacker-logo-icon.svg";
import { blendDarken } from "../../../containers/theme/helpers";
import { useWindows, WindowKind } from "../../../windowManager";

const ASSISTANT_BUTTON = {
    right: 24,
    bottom: 8,
    width: 75,
    height: 75,
};
export const AI_ASSISTANT_MODAL_ID = "AI_ASSISTANT";

export const AiAssistantButton = () => {
    const { open, close } = useWindows();
    const { windows } = useWindowManager();

    const handleClick = useCallback(() => {
        const openedAiAssistantDialog = windows.find((window) => window.id === AI_ASSISTANT_MODAL_ID);

        if (openedAiAssistantDialog) {
            close();
        } else {
            open({
                id: AI_ASSISTANT_MODAL_ID,
                isModal: false,
                kind: WindowKind.aiAssistant,
                title: "AI Assistant",
                isResizable: true,
                layoutData: {
                    right: ASSISTANT_BUTTON.right,
                    bottom: ASSISTANT_BUTTON.bottom + ASSISTANT_BUTTON.height + 20,
                    width: "35vw",
                    minWidth: 500,
                    height: "80vh",
                    zIndex: 3,
                },
            });
        }
    }, [close, open, windows]);

    return (
        <Box
            role="button"
            bottom={ASSISTANT_BUTTON.bottom}
            right={ASSISTANT_BUTTON.right}
            position={"fixed"}
            zIndex={1800}
            p={2}
            onClick={handleClick}
            sx={(theme) => ({
                background: blendDarken(theme.palette.primary.main, 0.6),
                cursor: "pointer",
                width: ASSISTANT_BUTTON.width,
                height: ASSISTANT_BUTTON.height,
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
    );
};
