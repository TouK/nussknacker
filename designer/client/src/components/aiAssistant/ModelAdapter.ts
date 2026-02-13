import type { ChatModelAdapter, ChatModelRunResult, ThreadAssistantMessagePart } from "@assistant-ui/react";
import type { AssistantMessage } from "assistant-stream";
import { unstable_runPendingTools } from "assistant-stream";

import { initializeChatStream } from "./InitializeChatStream";
import { addPendingHumanResolver, resolveHumanAction } from "./PendingHumanResolvers";
import { setThreadId } from "./ThreadIdManager";

let controller: AbortController;
function forkSignal(parentSignal: AbortSignal): AbortSignal {
    controller?.abort("new signal");
    controller = new AbortController();

    if (parentSignal.aborted) {
        controller.abort(parentSignal.reason);
    } else {
        parentSignal.addEventListener("abort", () => controller.abort(parentSignal.reason), { once: true });
    }

    return controller.signal;
}

export const createModelAdapter = (debug = false): ChatModelAdapter => ({
    async *run(chatModelOptions): AsyncGenerator<ChatModelRunResult> {
        const abortSignal = forkSignal(chatModelOptions.abortSignal);
        const chatStream = initializeChatStream({ ...chatModelOptions, abortSignal }, debug);

        let text = "";
        let calls: ThreadAssistantMessagePart[] = [];

        for await (const event of chatStream) {
            switch (event.type) {
                case "start":
                    yield {
                        content: [{ type: "text", text }, ...calls],
                        status: { type: "running" },
                    };
                    break;
                case "tool":
                    {
                        const toolCallId = event.callId || crypto.randomUUID();
                        calls.push({
                            type: "tool-call",
                            toolCallId,
                            toolName: event.name,
                            args: event.arguments,
                            argsText: JSON.stringify(event.arguments),
                        });
                        console.debug(calls[calls.length - 1]);
                        const message: ChatModelRunResult = {
                            content: [{ type: "text", text }, ...calls],
                            status: { type: "requires-action", reason: "tool-calls" },
                        };
                        yield message;
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
                    {
                        setThreadId(event.threadId);
                        const assistantMessage = await unstable_runPendingTools(
                            {
                                status: { type: "requires-action", reason: "tool-calls" },
                                parts: [{ type: "text", text }, ...calls],
                            } as AssistantMessage,
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
                    return;
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
});
