import type { ChatModelRunOptions, TextContentPart } from "@assistant-ui/react";
import { AssistantRuntimeProvider, useLocalRuntime, type ChatModelAdapter } from "@assistant-ui/react";
import { createParser } from "eventsource-parser";
import type { ReactNode } from "react";
import React from "react";

import httpService from "../../http/HttpService";

const backendApi = async function* ({ messages, abortSignal }: ChatModelRunOptions) {
    const response = await httpService.sendChatMessage(messages[messages.length - 1].content[0] as TextContentPart, abortSignal);
    const responseParts: string[] = [];

    const parser = createParser({
        onEvent({ event, data }) {
            if (event === "delta") {
                responseParts.push(JSON.parse(data).text);
            }
        },
    });

    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    const readStream = async () => {
        // eslint-disable-next-line no-constant-condition
        while (true) {
            const { value, done } = await reader.read();
            if (done) {
                console.log("done", "true");
                break;
            }
            parser.feed(value);
        }
    };

    readStream();

    if (abortSignal) {
        abortSignal.addEventListener("abort", () => {
            reader.cancel();
        });
    }

    while (true) {
        if (responseParts.length > 0) {
            const part = responseParts.shift();
            yield {
                choices: [{ delta: { content: part } }],
            };
        } else {
            await new Promise((resolve) => setTimeout(resolve, 100));
        }
    }
};

const MyModelAdapter: ChatModelAdapter = {
    async *run(chatModelOptions) {
        const stream = backendApi(chatModelOptions);

        yield {
            content: [],
            status: { type: "running" },
        };

        let text = "";
        for await (const part of stream) {
            text += part.choices[0]?.delta?.content || "";

            yield {
                content: [{ type: "text", text }],
                status: { type: "incomplete", reason: "other" },
            };
        }

        yield {
            content: [{ type: "text", text }],
            status: { type: "complete", reason: "unknown" },
        };
    },
};

export function AiAssistantProvider({
    children,
}: Readonly<{
    children: ReactNode;
}>) {
    const runtime = useLocalRuntime(MyModelAdapter);

    return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>;
}
