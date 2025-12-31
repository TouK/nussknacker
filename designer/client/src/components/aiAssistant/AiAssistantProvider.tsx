import type { ChatModelRunOptions, TextMessagePart } from "@assistant-ui/react";
import { AssistantRuntimeProvider, type ChatModelAdapter, useLocalRuntime } from "@assistant-ui/react";
import type { EventSourceMessage } from "eventsource-parser";
import { EventSourceParserStream } from "eventsource-parser/stream";
import type { ReactNode } from "react";
import React, { useEffect } from "react";

import httpService from "../../http/HttpService/instance";
import { addListenerTyped, useAppDispatch } from "../../store/storeHelpers";
import { prepareHelpMessage } from "./prepareHelpMessage";

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

type ChatStreamEvent =
    | { type: "delta"; responsePart: string }
    | { type: "stop"; threadId: string }
    | { type: "aborted" }
    | { type: "error" }
    | { type: "unknown"; originalType?: string; data: string };

const parseEvent: (eventSourceMessage: EventSourceMessage) => ChatStreamEvent = (eventSourceMessage) => {
    if (eventSourceMessage.event === "delta") {
        return { type: "delta", responsePart: JSON.parse(eventSourceMessage.data).text };
    } else if (eventSourceMessage.event === "stop") {
        return { type: "stop", threadId: JSON.parse(eventSourceMessage.data).threadId };
    } else if (eventSourceMessage.event == "error") {
        return { type: "error" };
    } else {
        return { type: "unknown", originalType: eventSourceMessage.event, data: eventSourceMessage.data };
    }
};

function smoothTransform(baseDelay: number, nth = 1) {
    let lastTime = performance.now();
    let counter = 0;
    return new TransformStream({
        async transform(chunk, controller) {
            counter = (counter + 1) % nth;
            if (counter === 0) {
                const now = performance.now();
                const sinceLast = now - lastTime;
                const effectiveDelay = sinceLast < baseDelay ? baseDelay - sinceLast : 0;
                if (effectiveDelay > 0) {
                    await new Promise((r) => setTimeout(r, effectiveDelay));
                }
            }
            lastTime = performance.now();
            controller.enqueue(chunk);
        },
    });
}

async function* initializeChatStream(
    messages: ChatModelRunOptions["messages"],
    abortSignal?: AbortSignal,
): AsyncGenerator<ChatStreamEvent> {
    const response = await httpService.sendChatMessage(
        messages[messages.length - 1].content[0] as TextMessagePart,
        abortSignal,
        ThreadIdManager.THREAD_ID,
    );

    if (!response.ok) {
        console.error("Failed to fetch chat stream: ", response.statusText);
        yield { type: "error" };
        return;
    }

    const reader = response.body
        .pipeThrough(new TextDecoderStream())
        .pipeThrough(new EventSourceParserStream())
        .pipeThrough(smoothTransform(100, 8))
        .getReader();

    const abortHandler = () => {
        try {
            reader.cancel("Operation aborted");
        } catch (error) {
            console.warn("Error canceling reader: ", error);
        }
    };

    if (abortSignal) {
        abortSignal.addEventListener("abort", abortHandler);
    }

    try {
        while (true) {
            try {
                const { done, value } = await reader.read();
                if (done) break;
                yield parseEvent(value);
            } catch (error) {
                if (abortSignal?.aborted) {
                    yield { type: "aborted" };
                    break;
                }
                console.error("Error while reading from chat stream: ", error);
                yield { type: "error" };
            }
        }
    } finally {
        if (abortSignal) {
            abortSignal.removeEventListener("abort", abortHandler);
        }
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
                ThreadIdManager.THREAD_ID = event.threadId;
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "complete", reason: "stop" },
                };
                return;
            } else if (event.type === "aborted") {
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "incomplete", reason: "cancelled" },
                };
            } else if (event.type === "error") {
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "incomplete", reason: "error", error: "An unexpected error occurred. Please try again." },
                };
                return;
            } else if (event.type === "unknown") {
                console.warn("Received unknown event type: ", event.originalType, " with data: ", event.data);
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "incomplete", reason: "error", error: "Unknown event received. Please try again." },
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

    const dispatch = useAppDispatch();
    useEffect(() => {
        return dispatch(
            addListenerTyped("ASSISTANT_ASK", ({ question, realPrompt }) => {
                runtime.thread.append(prepareHelpMessage(question, realPrompt));
            }),
        );
    }, [dispatch, runtime.thread]);

    return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>;
}
