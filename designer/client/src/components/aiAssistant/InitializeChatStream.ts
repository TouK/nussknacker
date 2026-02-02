//TODO: Find out how to pass threadId to the AiAssistant and use it instead of this variable
import type { ChatModelRunOptions } from "@assistant-ui/react";
import type { EventSourceMessage } from "eventsource-parser";
import { EventSourceParserStream } from "eventsource-parser/stream";

import httpService from "../../http/HttpService/instance";
import { extractMessage, extractTools } from "./messageHelpers";
import { ThreadIdManager } from "./ThreadIdManager";

export type ChatStreamEventName = "toolExecutionRequest" | "delta" | "stop" | "error" | NonNullable<string>;

type ChatStreamParsedEvent =
    | { type: "tool"; name: string; arguments: Record<string, any> }
    | { type: "delta"; responsePart: string }
    | { type: "stop"; threadId: string }
    | { type: "aborted" }
    | { type: "error" }
    | { type: "unknown"; originalType?: string; data: string };

type ChatEventSourceMessage = EventSourceMessage & {
    event: ChatStreamEventName;
};

const parseEvent: (eventSourceMessage: ChatEventSourceMessage) => ChatStreamParsedEvent = (eventSourceMessage) => {
    switch (eventSourceMessage.event) {
        case "toolExecutionRequest": {
            const parsed = JSON.parse(eventSourceMessage.data);
            return {
                type: "tool",
                name: parsed.name,
                arguments: parsed.arguments,
            };
        }
        case "delta":
            return { type: "delta", responsePart: JSON.parse(eventSourceMessage.data).text };
        case "stop":
            return { type: "stop", threadId: JSON.parse(eventSourceMessage.data).threadId };
        case "error":
            return { type: "error" };
        default:
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

type ChatStream = AsyncGenerator<ChatStreamParsedEvent>;

export async function* initializeChatStream({ abortSignal, context, messages, unstable_getMessage }: ChatModelRunOptions): ChatStream {
    const message = extractMessage(messages, unstable_getMessage());
    if (!message) return;

    const response = await httpService.sendChatMessage(
        {
            threadId: ThreadIdManager.THREAD_ID,
            message: message,
            externalTools: extractTools(context),
        },
        abortSignal,
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
