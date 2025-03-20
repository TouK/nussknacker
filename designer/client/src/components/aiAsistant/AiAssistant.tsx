import React from "react";
import { Thread } from "@assistant-ui/react-ui";
import { Composer } from "./components/Composer";
import { UserMessage } from "./components/UserMessage";
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
                    // MessagesFooter: () => <div>This is a footer message</div>,
                    // AssistantMessage: () => <div>This is an assistant message</div>,
                    // ThreadWelcome: () => <div>Welcome to the thread</div>,
                    EditComposer: () => <>works</>,
                }}
            />
            <Composer />
        </StyledRoot>
    );
};
