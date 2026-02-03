import { useMessage } from "@assistant-ui/react";
import { Box, Paper, styled, Typography } from "@mui/material";
import React, { useMemo } from "react";

import { ActionsContainer, useHandleActions } from "./actions/ActionsContainer";
import { CopyContent } from "./actions/CopyContent";

const Container = styled(Box)(({ theme }) => ({
    display: "flex",
    alignItems: "flex-end",
    flexDirection: "column",
    width: "100%",
    position: "relative",
    paddingBottom: theme.spacing(2),
}));

const MessageBubble = styled(Paper)(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    backgroundColor: theme.palette.grey[800],
    padding: theme.spacing(1),
    maxWidth: "70%",
}));

export const UserMessage = () => {
    const content = useMessage(({ content }) => content);
    const metadata = useMessage(({ metadata }) => metadata);
    const messageText = useMemo(
        () =>
            content
                .map((part, index) => (metadata.custom.hiddenHelpRequest && index === 0 ? null : part.text))
                .filter(Boolean)
                .join("\n"),
        [content, metadata],
    );
    const { showActions, handleShowActions, handleHideActions } = useHandleActions();

    if (!messageText.length) return null;

    return (
        <Container onMouseEnter={handleShowActions} onMouseLeave={handleHideActions}>
            <MessageBubble>
                <Typography variant={"body2"} sx={{ whiteSpace: "pre-line" }}>
                    {messageText}
                </Typography>
            </MessageBubble>
            <ActionsContainer show={showActions}>
                <CopyContent text={messageText} />
            </ActionsContainer>
        </Container>
    );
};
