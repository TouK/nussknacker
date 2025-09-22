import { Box, styled, Typography } from "@mui/material";
import { useWindowManager } from "@touk/window-manager";
import React, { useCallback, useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";

import NuIcon from "../../../assets/img/nussknacker-logo-icon.svg";
import { blendDarken } from "../../../containers/theme/helpers";
import { getFeatureSettings } from "../../../reducers/selectors/settings";
import { useAppSelector } from "../../../store/storeHelpers";
import { useWindows } from "../../../windowManager/useWindows";
import { WindowKind } from "../../../windowManager/WindowKind";
import DragWrapper from "./DragWrapper";

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
// TODO: use Fab
const StyledAiAssistantButton = styled(Box)(({ theme }) => ({
    width: 75,
    height: 75,
    padding: theme.spacing(2),
    background: blendDarken(theme.palette.primary.main, 0.6),
    cursor: "pointer",
    borderRadius: "50%",
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
    userSelect: "none",
    boxShadow: theme.shadows[6],
    "&:active": {
        boxShadow: theme.shadows[12],
    },
}));

export const AI_ASSISTANT_MODAL_ID = "AI_ASSISTANT";

export const AiAssistantButton = () => {
    const { t } = useTranslation();
    const { open } = useWindows();
    const { windows, close } = useWindowManager();
    const featureSettings = useAppSelector(getFeatureSettings);
    const openedAiAssistantDialog = useMemo(() => Boolean(windows.find((window) => window.id === AI_ASSISTANT_MODAL_ID)), [windows]);

    const buttonRef = useRef<HTMLElement>(null);
    const handleClick = useCallback(() => {
        if (openedAiAssistantDialog) {
            close(AI_ASSISTANT_MODAL_ID);
        } else {
            const buttonBox = buttonRef.current.getBoundingClientRect();
            open({
                id: AI_ASSISTANT_MODAL_ID,
                isModal: false,
                kind: WindowKind.aiAssistant,
                title: "AI Assistant",
                isResizable: true,
                layoutData: {
                    right: window.innerWidth - buttonBox.right,
                    bottom: window.innerHeight - buttonBox.top,
                    width: convertViewportUnitToPixels("35vw"),
                    minWidth: 500,
                    minHeight: 200,
                    height: convertViewportUnitToPixels("80vh"),
                    zIndex: 2,
                },
            });
        }
    }, [close, open, openedAiAssistantDialog]);

    if (!featureSettings.assistant.enabled) {
        return null;
    }

    return (
        <DragWrapper
            sx={(theme) => ({
                position: "fixed",
                bottom: 8,
                right: 12,
                zIndex: openedAiAssistantDialog ? theme.zIndex.modal - 10 : theme.zIndex.modal, // In case of modal full screen we want to hide button to not overlap other elements
            })}
            onClick={handleClick}
        >
            <StyledAiAssistantButton ref={buttonRef} id="ai-assistant-button" role="button">
                <NuIcon />
                <Typography component="span" variant={"overline"} fontWeight={"bold"} pt={0.5}>
                    {t("aiAssistant.buttonText", "Assistant")}
                </Typography>
            </StyledAiAssistantButton>
        </DragWrapper>
    );
};
