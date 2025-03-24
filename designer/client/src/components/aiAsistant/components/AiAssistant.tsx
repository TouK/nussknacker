import React from "react";
import { Thread } from "@assistant-ui/react-ui";
import { UserMessage } from "./UserMessage";
import { AssistantMessage } from "./AssistantMessage";
import { Box, Divider, Paper, styled, Typography } from "@mui/material";
import { Composer } from "./Composer";

const StyledAiAssistant = styled("div")(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    height: "calc(70vh - 80px)", // Adjust for header and divider
    ".aui-root": {
        height: "100%",
    },
    ".aui-thread-viewport": {
        height: "100%",
        display: "flex",
        flexDirection: "column",
        overflowY: "auto",
    },
    ".aui-thread-viewport-footer": {
        marginTop: "auto",
    },
    ".aui-thread-scroll-to-bottom": {
        display: "none",
    },
}));

export const AiAssistant = () => {
    return (
        <Box position={"fixed"} bottom={120} right={10} zIndex={1800}>
            <Paper sx={{ height: "70vh", width: 500, p: 2 }}>
                <Typography variant={"subtitle2"}>AI Assistant</Typography>
                <Divider sx={{ my: 2 }} />
                <StyledAiAssistant>
                    <Thread
                        assistantAvatar={{ src: undefined }}
                        components={{
                            UserMessage,
                            Composer: () => <></>,
                            AssistantMessage,
                        }}
                    />
                    <Composer />
                </StyledAiAssistant>
            </Paper>
        </Box>
    );
};
