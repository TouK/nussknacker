import { MessagePrimitive, ThreadPrimitive } from "@assistant-ui/react";
import type { WindowContentProps } from "@touk/window-manager";
import type { ComponentProps } from "react";
import React, { useMemo } from "react";

import { WindowContent } from "../../../windowManager/WindowContent";
import { AssistantMessage } from "./AssistantMessage";
import { Composer } from "./Composer";
import { DefaultToolComponent } from "./DefaultToolComponent";
import { ResetThread } from "./ResetThread";
import { ThreadSuggestions } from "./ThreadWelcome";
import { ThreadWithScroll } from "./ThreadWithScroll";
import { UserMessage } from "./UserMessage";

const AiAssistantModal = ({ ...props }: WindowContentProps) => {
    const components = useMemo<ComponentProps<typeof WindowContent>["components"]>(
        () => ({
            Footer: () => <Composer />,
        }),
        [],
    );

    return (
        <WindowContent {...props} components={components} closeWithEsc subheader={<ResetThread />}>
            <ThreadWithScroll>
                <ThreadSuggestions />
                <ThreadPrimitive.Messages
                    components={{
                        UserMessage: UserMessagePrimitive,
                        AssistantMessage: AssistantMessagePrimitive,
                    }}
                />
            </ThreadWithScroll>
        </WindowContent>
    );
};

function UserMessagePrimitive() {
    return (
        <MessagePrimitive.Root>
            <UserMessage />
        </MessagePrimitive.Root>
    );
}

function AssistantMessagePrimitive() {
    return (
        <MessagePrimitive.Root>
            <MessagePrimitive.Content
                components={{
                    Text: AssistantMessage,
                    tools: {
                        Fallback: DefaultToolComponent,
                    },
                }}
            />
        </MessagePrimitive.Root>
    );
}

export default AiAssistantModal;
