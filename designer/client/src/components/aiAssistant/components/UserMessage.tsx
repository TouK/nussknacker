import { useMessage } from "@assistant-ui/react";
import { styled } from "@mui/material";
import { Box, Typography } from "@mui/material";
import React, { useMemo } from "react";

import { CopyContent } from "./CopyContent";

const Container = styled(Box)(({ theme }) => ({
    display: "flex",
    alignItems: "flex-end",
    flexDirection: "column",
    width: "100%",
    position: "relative",
    paddingBottom: theme.spacing(2),
    "&:hover .copyIcon": {
        display: "flex",
    },
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

    const messageText = useMemo(() => content.map((part) => part.text).join("\n"), [content]);

    return (
        <Container>
            <MessageBubble>
                <Typography sx={{ whiteSpace: "pre-line" }}>{messageText}</Typography>
            </MessageBubble>
            <CopyContent text={messageText} />
        </Container>
    );
};
