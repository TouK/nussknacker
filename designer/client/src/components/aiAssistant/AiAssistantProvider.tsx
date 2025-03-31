import { AssistantRuntimeProvider, useLocalRuntime, type ChatModelAdapter } from "@assistant-ui/react";
import { createParser } from "eventsource-parser";
import type { ReactNode } from "react";
import React from "react";

import httpService from "../../http/HttpService";

const backendApi = async function* ({ messages, abortSignal, context }) {
    const response = await httpService.sendChatMessage(messages[messages.length - 1].content[0]);
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
            if (done) break;
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
    async *run({ messages, abortSignal, context }) {
        const stream = await backendApi({ messages, abortSignal, context });

        let text = "";
        for await (const part of stream) {
            text += part.choices[0]?.delta?.content || "";

            yield {
                content: [{ type: "text", text }],
            };
        }
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
