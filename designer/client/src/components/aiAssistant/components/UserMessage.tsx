import { useMessage } from "@assistant-ui/react";
import { styled } from "@mui/material";
import { Box, Typography } from "@mui/material";
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

const MessageBubble = styled(Box)(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    backgroundColor: theme.palette.grey[700],
    borderRadius: theme.spacing(0.5),
    padding: theme.spacing(1),
    maxWidth: "70%",
}));

export const UserMessage = () => {
    const { content } = useMessage();
    const { showActions, handleShowActions, handleHideActions } = useHandleActions();

    const messageText = useMemo(() => content.map((part) => part.text).join("\n"), [content]);

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
