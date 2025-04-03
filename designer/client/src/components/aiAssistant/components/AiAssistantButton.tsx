import { Box, styled, Typography } from "@mui/material";
import { useWindowManager } from "@touk/window-manager";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import NuIcon from "../../../assets/img/nussknacker-logo-icon.svg";
import { useUserSettings } from "../../../common/userSettings";
import { blendDarken } from "../../../containers/theme/helpers";
import { isCloudInstance } from "../../../reducers/selectors/isCloudInstance";
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

const StyledAiAssistantButton = styled(Box)<{ isOpenedAiAssistantDialog: boolean }>(({ theme, isOpenedAiAssistantDialog }) => {
    return {
        position: "fixed",
        bottom: ASSISTANT_BUTTON.bottom,
        right: ASSISTANT_BUTTON.right,
        padding: theme.spacing(2),
        background: blendDarken(theme.palette.primary.main, 0.6),
        cursor: "pointer",
        width: ASSISTANT_BUTTON.width,
        height: ASSISTANT_BUTTON.height,
        borderRadius: "50%",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        zIndex: isOpenedAiAssistantDialog ? theme.zIndex.modal : theme.zIndex.modal + 1,
    };
});

export const AI_ASSISTANT_MODAL_ID = "AI_ASSISTANT";

export const AiAssistantButton = () => {
    const { t } = useTranslation();
    const { open } = useWindows();
    const { windows, close } = useWindowManager();
    const [userSettings] = useUserSettings();
    const isCloud = useSelector(isCloudInstance);
    const openedAiAssistantDialog = useMemo(() => windows.find((window) => window.id === AI_ASSISTANT_MODAL_ID), [windows]);

    const handleClick = useCallback(() => {
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
    }, [close, open, openedAiAssistantDialog]);

    if (!isCloud || !userSettings["cloud.showAiAssistant"]) {
        return null;
    }

    return (
        <StyledAiAssistantButton
            id="ai-assistant-button"
            role="button"
            onClick={handleClick}
            isOpenedAiAssistantDialog={Boolean(openedAiAssistantDialog)}
        >
            <NuIcon />
            <Typography component="span" variant={"overline"} fontWeight={"bold"} pt={0.5}>
                {t("aiAssistant.buttonText", "Assistant")}
            </Typography>
        </StyledAiAssistantButton>
    );
};
