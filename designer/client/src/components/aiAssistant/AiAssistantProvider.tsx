import type { ChatModelRunOptions, TextContentPart } from "@assistant-ui/react";
import { AssistantRuntimeProvider, type ChatModelAdapter, useLocalRuntime } from "@assistant-ui/react";
import { createParser } from "eventsource-parser";
import type { ReactNode } from "react";
import React from "react";

import httpService from "../../http/HttpService";

async function* initializeChatStream(
    messages: ChatModelRunOptions["messages"],
    abortSignal?: AbortSignal,
): AsyncGenerator<{
    responsePart?: string;
    state: { isAborted: boolean; isFinished: boolean };
}> {
    const response = await httpService.sendChatMessage(messages[messages.length - 1].content[0] as TextContentPart, abortSignal);
    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    while (true) {
        if (abortSignal?.aborted) {
            yield { state: { isAborted: true, isFinished: false } };
            await reader.cancel();
            return;
        }

        const { done, value } = await reader.read();

        if (done) {
            return;
        }

        const events = [];
        const parser = createParser({
            onEvent({ event, data }) {
                if (event === "delta") {
                    events.push({
                        responsePart: JSON.parse(data).text,
                        state: { isAborted: false, isFinished: false },
                    });
                }
                if (event === "stop") {
                    events.push({
                        state: { isAborted: false, isFinished: true },
                    });
                }
            },
        });
        parser.feed(value);
        for (const event of events) {
            yield event;
        }
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
            const { responsePart, state } = event;
            if (state.isAborted) {
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "incomplete", reason: "cancelled" },
                };
                return;
            } else if (state.isFinished) {
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "complete", reason: "stop" },
                };
                return;
            } else if (responsePart !== null) {
                text += responsePart;
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "running" },
                };
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
