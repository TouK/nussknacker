import type { ChatModelAdapter, ChatModelRunResult, ToolCallMessagePart } from "@assistant-ui/react";
import type { AssistantMessage } from "assistant-stream";
import { unstable_runPendingTools } from "assistant-stream";

import { initializeChatStream, ThreadIdManager } from "./InitializeChatStream";
import { addPendingHumanResolver, resolveHumanAction } from "./PendingHumanResolvers";

let controller: AbortController;
function forkSignal(parentSignal: AbortSignal): AbortSignal {
    controller?.abort();
    controller = new AbortController();

    if (parentSignal.aborted) {
        controller.abort(parentSignal.reason);
    } else {
        parentSignal.addEventListener("abort", () => controller.abort(parentSignal.reason), { once: true });
    }

    return controller.signal;
}

export const ModelAdapter: ChatModelAdapter = {
    async *run(chatModelOptions): AsyncGenerator<ChatModelRunResult> {
        const abortSignal = forkSignal(chatModelOptions.abortSignal);
        const chatStream = initializeChatStream({ ...chatModelOptions, abortSignal });

        yield {
            content: [],
            status: { type: "running" },
        };

        let text = "";
        let calls: ToolCallMessagePart[] = [];

        for await (const event of chatStream) {
            switch (event.type) {
                case "tool":
                    {
                        calls.push({
                            type: "tool-call",
                            toolCallId: crypto.randomUUID(),
                            toolName: event.name,
                            args: event.arguments,
                            argsText: JSON.stringify(event.arguments),
                        });
                        const message: ChatModelRunResult = {
                            content: [{ type: "text", text }, ...calls],
                            status: { type: "requires-action", reason: "tool-calls" },
                        };
                        yield message;

                        const assistantMessage = await unstable_runPendingTools(
                            { ...message, parts: message.content } as AssistantMessage,
                            chatModelOptions.context.tools,
                            abortSignal,
                            (toolCallId, payload) => {
                                abortSignal.addEventListener("abort", () => resolveHumanAction(toolCallId), { once: true });
                                return addPendingHumanResolver(toolCallId, typeof payload === "function" ? payload() : payload);
                            },
                        );
                        calls = assistantMessage.parts.map((p) => p.type === "tool-call" && p).filter(Boolean);
                        yield assistantMessage;
                    }
                    break;
                case "delta":
                    text += event.responsePart;
                    yield {
                        content: [{ type: "text", text }, ...calls],
                        status: { type: "running" },
                    };
                    break;
                case "stop":
                    ThreadIdManager.THREAD_ID = event.threadId;
                    break;
                case "aborted":
                    yield {
                        status: { type: "incomplete", reason: "cancelled" },
                    };
                    return;
                case "error":
                    yield {
                        status: {
                            type: "incomplete",
                            reason: "error",
                            error: "An unexpected error occurred. Please try again.",
                        },
                    };
                    return;
                case "unknown":
                    console.warn("Received unknown event type: ", event.originalType, " with data: ", event.data);
                    yield {
                        status: {
                            type: "incomplete",
                            reason: "error",
                            error: "Unknown event received. Please try again.",
                        },
                    };
                    return;
            }
        }
    },
};
