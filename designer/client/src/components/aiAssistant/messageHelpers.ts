import type { ChatModelRunOptions, ThreadMessage } from "@assistant-ui/react";
import { ToolResponse } from "assistant-stream";
import { mapValues, pick } from "lodash";

import type { ChatRequest } from "./ChatRequest";

function getLastUserMessage(messages: readonly ThreadMessage[]) {
    const userMessages = messages.filter((m) => m.role === "user");
    return userMessages[userMessages.length - 1];
}

const EMPTY_RESPONSES = [undefined, null, ""].map((v) => ToolResponse.toResponse(v).result);

export function extractMessage(messages: ChatModelRunOptions["messages"], lastAssistantMessage?: ThreadMessage): ChatRequest["message"] {
    if (lastAssistantMessage?.content.length > 0 && !messages.find((m) => m.id === lastAssistantMessage.id)) {
        if (lastAssistantMessage.content[lastAssistantMessage.content.length - 1].type === "tool-call") {
            const results = lastAssistantMessage?.content
                .map((c) => {
                    if (c.type !== "tool-call") return null;
                    if (c.isError) return null;
                    if (EMPTY_RESPONSES.includes(c.result)) return null;
                    return `${c.toolName}: ${JSON.stringify(c.result, null, 2)}`;
                })
                .filter(Boolean);
            if (results?.length > 0) {
                return { text: results.join("\n\n") };
            }
        }
        return;
    }

    const lastUserMessage = getLastUserMessage(messages);
    const contentElement = lastUserMessage?.content[0];
    if (contentElement.type === "text" && contentElement.text) {
        return { text: contentElement.text };
    }

    return { text: "" };
}

function pickBasic(schema) {
    const picked = pick(schema, ["type", "properties", "required", "items", "description"]);
    if (picked.properties) {
        picked.properties = mapValues(picked.properties, pickBasic);
    }
    if (picked.items) {
        picked.items = pickBasic(picked.items);
    }
    return picked;
}

export function extractTools({ tools }: ChatModelRunOptions["context"]): ChatRequest["externalTools"] {
    if (!tools) return [];
    return Object.entries(tools)
        .map(([name, { type, description, parameters }]) => {
            if (type !== "frontend") return;
            return { name, description, parameters: pickBasic(parameters) };
        })
        .filter(Boolean);
}
