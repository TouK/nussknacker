import type { ChatModelRunOptions, TextContentPart } from "@assistant-ui/react";
import { AssistantRuntimeProvider, type ChatModelAdapter, useLocalRuntime } from "@assistant-ui/react";
import type { EventSourceMessage } from "eventsource-parser";
import { EventSourceParserStream } from "eventsource-parser/stream";
import type { ReactNode } from "react";
import React from "react";

import httpService from "../../http/HttpService";

//TODO: Find out how to pass threadId to the AiAssistant and use it instead of this variable
const ThreadIdManager = {
    _threadId: undefined as string | undefined,

    get THREAD_ID() {
        return this._threadId;
    },

    set THREAD_ID(value: string | undefined) {
        this._threadId = value;
    },
};

type ChatStreamEvent = { type: "delta"; responsePart: string } | { type: "stop" };

const parseEvent: (eventSourceMessage: EventSourceMessage) => ChatStreamEvent = (eventSourceMessage) => {
    if (eventSourceMessage.event === "delta") {
        return { type: "delta", responsePart: JSON.parse(eventSourceMessage.data).text };
    } else if (eventSourceMessage.event === "stop") {
        ThreadIdManager.THREAD_ID = JSON.parse(eventSourceMessage.data).threadId;
        return { type: "stop" };
    }
    return;
};

async function* initializeChatStream(
    messages: ChatModelRunOptions["messages"],
    abortSignal?: AbortSignal,
): AsyncGenerator<ChatStreamEvent> {
    const response = await httpService.sendChatMessage(
        messages[messages.length - 1].content[0] as TextContentPart,
        abortSignal,
        ThreadIdManager.THREAD_ID,
    );

    const reader = response.body.pipeThrough(new TextDecoderStream()).pipeThrough(new EventSourceParserStream()).getReader();

    try {
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            yield parseEvent(value);
        }
    } finally {
        reader.releaseLock();
    }
}

const ModelAdapter: ChatModelAdapter = {
    async *run(chatModelOptions) {
        const chatStream = initializeChatStream(chatModelOptions.messages, chatModelOptions.abortSignal);

        yield {
            content: [],
            status: { type: "running" },
        };

        let text = "";
        for await (const event of chatStream) {
            if (event.type === "delta") {
                text += event.responsePart;
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "running" },
                };
            } else if (event.type === "stop") {
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "complete", reason: "stop" },
                };
                return;
            }
        }
    },
};

export function AiAssistantProvider({
    children,
}: Readonly<{
    children: ReactNode;
}>) {
    const runtime = useLocalRuntime(ModelAdapter);

    return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>;
}
