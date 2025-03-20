import React, { ReactNode } from "react";
import { AssistantRuntimeProvider, useLocalRuntime, type ChatModelAdapter } from "@assistant-ui/react";

const backendApi = async function* ({ messages, abortSignal, context }) {
    console.log({ messages });

    // Sample message parts to simulate streaming
    const responseParts = [
        "Hello",
        ", I'm analyzing your deployment parameters",
        ".\n\nYour process ",
        "has several components that need configuration",
        ".\n\nHave you considered ",
        "adding validation rules to your parameters?",
        " This would ensure data integrity during deployment."
    ];

    for (const part of responseParts) {
        // Check if request was aborted
        if (abortSignal?.aborted) {
            throw new Error("Request aborted");
        }

        // Yield each part with a delay to simulate streaming
        yield {
            choices: [{ delta: { content: part } }]
        };

        // Simulate network delay between chunks
        await new Promise(resolve => setTimeout(resolve, 300));
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

export function MyRuntimeProvider({
    children,
}: Readonly<{
    children: ReactNode;
}>) {
    const runtime = useLocalRuntime(MyModelAdapter);

    return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>;
}
