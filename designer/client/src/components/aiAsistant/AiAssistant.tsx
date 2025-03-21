import React from "react";
import { Thread } from "@assistant-ui/react-ui";
import { Composer } from "./components/Composer";
import { UserMessage } from "./components/UserMessage";
import { AssistantMessage } from "./components/AssistantMessage";
import { styled } from "@mui/material";

const StyledRoot = styled("div")(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    justifyContent: "space-between",
    height: "100%",
    ".aui-thread-viewport": {
        height: "400px",
        overflowY: "auto",
    },
    ".aui-thread-viewport-footer": {
        display: "none",
    },
}));

export const AiAssistant = () => {
    return (
        <StyledRoot>
            <Thread
                assistantAvatar={{ src: undefined }}
                components={{
                    UserMessage,
                    Composer: () => <></>,
                    AssistantMessage,
                }}
            />
            <Composer />
        </StyledRoot>
    );
};
