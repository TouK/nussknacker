import { Thread } from "@assistant-ui/react-ui";
import { styled } from "@mui/material";
import type { WindowContentProps } from "@touk/window-manager";
import React, { useMemo } from "react";

import { WindowContent } from "../../../windowManager";
import { AssistantMessage } from "./AssistantMessage";
import { Composer } from "./Composer";
import { ScrollToBottomButton } from "./ScrollToBottomButton";
import { ThreadSuggestions } from "./ThreadWelcome";
import { UserMessage } from "./UserMessage";

const StyledAiAssistant = styled("div")(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    margin: theme.spacing(2),
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

const AiAssistantModal = ({ ...props }: WindowContentProps) => {
    const components = useMemo(() => ({ Footer: () => <Composer /> }), []);

    return (
        <WindowContent {...props} components={components} closeWithEsc>
            <StyledAiAssistant>
                <Thread
                    assistantAvatar={{ src: undefined }}
                    components={{
                        UserMessage,
                        Composer: () => <></>,
                        AssistantMessage,
                        ThreadWelcome: ThreadSuggestions,
                    }}
                />
                <ScrollToBottomButton />
            </StyledAiAssistant>
        </WindowContent>
    );
};

export default AiAssistantModal;
