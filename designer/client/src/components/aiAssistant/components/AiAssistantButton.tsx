import { Box, Typography } from "@mui/material";
import { useWindowManager } from "@touk/window-manager";
import React, { useCallback } from "react";

import NuIcon from "../../../assets/img/nussknacker-logo-icon.svg";
import { blendDarken } from "../../../containers/theme/helpers";
import { useWindows, WindowKind } from "../../../windowManager";

function convertViewportUnitToPixels(unitString: string): number {
    const trimmed = unitString.trim();
    const value = parseFloat(trimmed);

    if (isNaN(value)) {
        throw new Error("Invalid input");
    }

    if (trimmed.endsWith("vw")) {
        return (value / 100) * window.innerWidth;
    } else if (trimmed.endsWith("vh")) {
        return (value / 100) * window.innerHeight;
    } else {
        throw new Error("Unsupported unit. Use 'vw' or 'vh'.");
    }
}

const ASSISTANT_BUTTON = {
    right: 12,
    bottom: 8,
    width: 75,
    height: 75,
};
export const AI_ASSISTANT_MODAL_ID = "AI_ASSISTANT";

export const AiAssistantButton = () => {
    const { open } = useWindows();
    const { windows, close } = useWindowManager();

    const handleClick = useCallback(() => {
        const openedAiAssistantDialog = windows.find((window) => window.id === AI_ASSISTANT_MODAL_ID);

        if (openedAiAssistantDialog) {
            close(AI_ASSISTANT_MODAL_ID);
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
                    width: convertViewportUnitToPixels("35vw"),
                    minWidth: 500,
                    height: convertViewportUnitToPixels("80vh"),
                    zIndex: 3,
                },
            });
        }
    }, [close, open, windows]);

    return (
        <Box
            id={"ai-assistant-button"}
            role="button"
            bottom={ASSISTANT_BUTTON.bottom}
            right={ASSISTANT_BUTTON.right}
            position={"fixed"}
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
