import type { ChatModelRunOptions, TextContentPart } from "@assistant-ui/react";
import { AssistantRuntimeProvider, useLocalRuntime, type ChatModelAdapter } from "@assistant-ui/react";
import { createParser } from "eventsource-parser";
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

async function initializeChatStream(
    messages: ChatModelRunOptions["messages"],
    abortSignal?: AbortSignal,
): Promise<{
    responseParts: string[];
    state: { isAborted: boolean; isFinished: boolean };
}> {
    const response = await httpService.sendChatMessage(
        messages[messages.length - 1].content[0] as TextContentPart,
        abortSignal,
        ThreadIdManager.THREAD_ID,
    );
    const responseParts: string[] = [];
    const state = { isAborted: false, isFinished: false };

    const parser = createParser({
        onEvent({ event, data }) {
            if (event === "delta") {
                responseParts.push(JSON.parse(data).text);
            }
            if (event === "stop") {
                state.isFinished = true;
                ThreadIdManager.THREAD_ID = JSON.parse(data).threadId;
            }
        },
    });

    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    const readStream = async () => {
        // eslint-disable-next-line no-constant-condition
        while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            parser.feed(value);
        }
    };
    readStream();

    if (abortSignal) {
        abortSignal.addEventListener("abort", () => {
            console.log("Message aborted");
            state.isAborted = true;
            reader.cancel();
        });
    }

    return { responseParts, state };
}

const ModelAdapter: ChatModelAdapter = {
    async *run(chatModelOptions) {
        const { responseParts, state } = await initializeChatStream(chatModelOptions.messages, chatModelOptions.abortSignal);

        yield {
            content: [],
            status: { type: "running" },
        };

        let text = "";
        while (true) {
            if (state.isAborted) {
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "incomplete", reason: "cancelled" },
                };
                return;
            }
            if (responseParts.length > 0) {
                const part = responseParts.shift();
                text += part;
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "running" },
                };
            } else if (state.isFinished && responseParts.length === 0) {
                yield {
                    content: [{ type: "text", text }],
                    status: { type: "complete", reason: "stop" },
                };
                return;
            } else {
                await new Promise((resolve) => setTimeout(resolve, 100));
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
