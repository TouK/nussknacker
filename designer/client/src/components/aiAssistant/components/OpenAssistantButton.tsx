import { Box, styled, Typography } from "@mui/material";
import { useWindowManager } from "@touk/window-manager";
import React, { useCallback, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";

import { assistantClose, assistantOpen } from "../../../actions/assistantActions";
import NuIcon from "../../../assets/img/nussknacker-logo-icon.svg";
import { convertViewportUnitToPixels } from "../../../common/convertViewportUnitToPixels";
import { blendDarken } from "../../../containers/theme/helpers";
import { addListenerTyped, useAppDispatch } from "../../../store/storeHelpers";
import { useWindows } from "../../../windowManager/useWindows";
import { WindowKind } from "../../../windowManager/WindowKind";
import DragWrapper from "./DragWrapper";

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

const AI_ASSISTANT_MODAL_ID = "AI_ASSISTANT";

const OpenAssistantButton = () => {
    const dispatch = useAppDispatch();
    const { t } = useTranslation();
    const { open } = useWindows();
    const { close, focus, frontWindow } = useWindowManager();

    const openedAiAssistantDialog = frontWindow === AI_ASSISTANT_MODAL_ID;

    const buttonRef = useRef<HTMLElement>(null);

    useEffect(
        () =>
            dispatch(
                addListenerTyped("ASSISTANT_OPEN", () => {
                    const buttonBox = buttonRef.current.getBoundingClientRect();
                    open({
                        parent: null,
                        id: AI_ASSISTANT_MODAL_ID,
                        kind: WindowKind.aiAssistant,
                        title: "AI Assistant",
                        isResizable: true,
                        isModal: false,
                        isGlobal: true,
                        layoutData: {
                            right: window.innerWidth - buttonBox.right,
                            bottom: window.innerHeight - buttonBox.top,
                            width: convertViewportUnitToPixels("35vw"),
                            minWidth: 500,
                            minHeight: 200,
                            height: convertViewportUnitToPixels("80vh"),
                        },
                    });
                }),
            ),
        [dispatch, open],
    );

    useEffect(
        () =>
            dispatch(
                addListenerTyped("ASSISTANT_CLOSE", () => {
                    close(AI_ASSISTANT_MODAL_ID);
                }),
            ),
        [dispatch, close],
    );

    useEffect(
        () =>
            dispatch(
                addListenerTyped("ASSISTANT_FOCUS", () => {
                    focus(AI_ASSISTANT_MODAL_ID);
                }),
            ),
        [dispatch, focus],
    );

    const handleClick = useCallback(() => {
        dispatch(openedAiAssistantDialog ? assistantClose() : assistantOpen());
    }, [dispatch, openedAiAssistantDialog]);

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

export default OpenAssistantButton;
