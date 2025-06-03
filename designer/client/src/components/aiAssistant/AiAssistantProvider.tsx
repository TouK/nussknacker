import type { ChatModelRunOptions, TextContentPart } from "@assistant-ui/react";
import { AssistantRuntimeProvider, type ChatModelAdapter, useLocalRuntime } from "@assistant-ui/react";
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

enum StreamStatus {
    STREAMING = "streaming",
    ABORTED = "aborted",
    FINISHED = "finished",
    ERROR = "error",
}

type StreamState =
    | { status: StreamStatus.STREAMING }
    | { status: StreamStatus.ABORTED }
    | { status: StreamStatus.FINISHED }
    | { status: StreamStatus.ERROR; error?: string };

async function initializeChatStream(
    messages: ChatModelRunOptions["messages"],
    abortSignal?: AbortSignal,
): Promise<{
    responseParts: string[];
    stateRef: { value: StreamState };
}> {
    const response = await httpService.sendChatMessage(
        messages[messages.length - 1].content[0] as TextContentPart,
        abortSignal,
        ThreadIdManager.THREAD_ID,
    );
    const responseParts: string[] = [];
    const stateRef: { value: StreamState } = { value: { status: StreamStatus.STREAMING } };

    const parser = createParser({
        onEvent({ event, data }) {
            if (event === "delta") {
                console.log("Received delta event:", data);
                responseParts.push(JSON.parse(data).text);
            }
            if (event === "stop") {
                console.log("Received stop event:", data);
                stateRef.value = { status: StreamStatus.FINISHED };
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
    readStream().catch((error) => {
        stateRef.value = { status: StreamStatus.ERROR, error: "An error occurred, please try again" };
    });

    if (abortSignal) {
        abortSignal.addEventListener("abort", () => {
            console.log("Message aborted");
            stateRef.value = { status: StreamStatus.ABORTED };
            reader.cancel();
        });
    }

    return { responseParts, stateRef };
}

const ModelAdapter: ChatModelAdapter = {
    async *run(chatModelOptions) {
        const { responseParts, stateRef } = await initializeChatStream(chatModelOptions.messages, chatModelOptions.abortSignal);

        yield {
            content: [],
            status: { type: "running" },
        };

        let text = "";
        while (true) {
            const state = stateRef.value;
            switch (state.status) {
                case StreamStatus.ABORTED:
                    yield {
                        content: [{ type: "text", text }],
                        status: { type: "incomplete", reason: "cancelled" },
                    };
                    return;
                case StreamStatus.STREAMING:
                    if (responseParts.length > 0) {
                        console.log("Streaming response parts:", responseParts.length);
                        const part = responseParts.shift();
                        text += part;
                        yield {
                            content: [{ type: "text", text }],
                            status: { type: "running" },
                        };
                    } else {
                        // Await for new tokens to arrive.
                        await new Promise((resolve) => setTimeout(resolve, 100));
                    }
                    break;
                case StreamStatus.FINISHED:
                    yield {
                        content: [{ type: "text", text }],
                        status: { type: "complete", reason: "stop" },
                    };
                    return;
                case StreamStatus.ERROR:
                    yield {
                        content: [{ type: "text", text }],
                        status: { type: "incomplete", reason: "error", error: state.error },
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
