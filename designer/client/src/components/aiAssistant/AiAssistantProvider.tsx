import { AssistantRuntimeProvider, useLocalRuntime, type ChatModelAdapter } from "@assistant-ui/react";
import type { ReactNode } from "react";
import React from "react";

const backendApi = async function* ({ messages, abortSignal, context }) {
    const response = await fetch("http://0.0.0.0:8080/sse", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({ question: messages[messages.length - 1].content[0].text, userId: "12345" }),
    });

    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    const responseParts = [];

    const readStream = async () => {
        // eslint-disable-next-line no-constant-condition
        while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            value.split("data: ").forEach((line) => {
                const chunk = line.replace(/\r?\n\r?\n$/, "");
                responseParts.push(chunk);
            });
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
