import { useMessage } from "@assistant-ui/react";
import { Box, CircularProgress, Typography } from "@mui/material";
import React, { useMemo } from "react";

import { MarkdownStyled } from "../../graph/node-modal/MarkdownStyled";
import { ActionsContainer, useHandleActions } from "./actions/ActionsContainer";
import { CopyContent } from "./actions/CopyContent";
import { RefreshAssistantAnswer } from "./actions/RefreshAssistantAnswer";

export const AssistantMessage = () => {
    const { status, content } = useMessage();
    const { showActions, handleShowActions, handleHideActions } = useHandleActions();

    const messageText = useMemo(() => content.map((part) => part.text).join("\n"), [content]);

    if (status.type === "running" && messageText.length === 0) {
        return (
            <Box display="flex" alignItems="center" gap={1}>
                <CircularProgress size="0.75rem" />
                <Typography variant={"body2"}>Running...</Typography>
            </Box>
        );
    }

    if ((status.type === "complete" && messageText.length === 0) || (status.type === "incomplete" && status.reason === "cancelled")) {
        return null;
    }

    if (status.type === "complete") {
        return (
            <Box position={"relative"} onMouseEnter={handleShowActions} onMouseLeave={handleHideActions}>
                <Box position={"relative"} pb={0.5}>
                    <MarkdownStyled>{messageText}</MarkdownStyled>
                    <ActionsContainer show={showActions} placement={"left"}>
                        <CopyContent text={messageText} />
                        <RefreshAssistantAnswer />
                    </ActionsContainer>
                </Box>
            </Box>
        );
    }

    if (status.type == "incomplete" && status.reason === "error") {
        return (
            <Box position={"relative"} onMouseEnter={handleShowActions} onMouseLeave={handleHideActions}>
                <Box position={"relative"} pb={0.5}>
                    <MarkdownStyled sx={{ color: "error.main" }}>{status.error?.toString()}</MarkdownStyled>
                    <ActionsContainer show={showActions} placement={"left"}>
                        <RefreshAssistantAnswer />
                    </ActionsContainer>
                </Box>
            </Box>
        );
    }
};
