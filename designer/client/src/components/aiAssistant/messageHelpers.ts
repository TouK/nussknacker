import type { ChatModelRunOptions, ThreadMessage } from "@assistant-ui/react";
import { ToolResponse } from "assistant-stream";
import { mapValues, omit } from "lodash";

import type {
    ChatRequest,
    ChatRequestWithMessage,
    ChatRequestWithToolResults,
    ToolCallId,
    ToolExecutionResult,
    ToolName,
    ToolResult,
} from "./ChatRequest";
import { wasToolResponseConsumed } from "./ThreadIdManager";

function getLastUserMessage(messages: readonly ThreadMessage[]) {
    const userMessages = messages.filter((m) => m.role === "user");
    return userMessages[userMessages.length - 1];
}

const EMPTY_RESPONSES = [undefined, null, ""].map((v) => ToolResponse.toResponse(v).result);

export function extractMessage(
    messages: ChatModelRunOptions["messages"],
    lastAssistantMessage?: ThreadMessage,
): ChatRequestWithMessage["message"] | null {
    if (lastAssistantMessage?.content.length > 0 && !messages.find((m) => m.id === lastAssistantMessage.id)) {
        return null;
    }

    const lastUserMessage = getLastUserMessage(messages);
    const contentElement = lastUserMessage?.content[0];
    if (contentElement.type === "text" && contentElement.text) {
        return { text: contentElement.text };
    }

    return { text: "" };
}

export function extractToolResults(
    messages: ChatModelRunOptions["messages"],
    lastAssistantMessage?: ThreadMessage,
): ChatRequestWithToolResults["toolExecutionResults"] | null {
    if (!lastAssistantMessage?.content.length) return null;
    if (messages.find((m) => m.id === lastAssistantMessage.id)) return null;

    const lastContent = lastAssistantMessage.content[lastAssistantMessage.content.length - 1];
    if (lastContent.type !== "tool-call") return null;

    const results: ToolExecutionResult[] = lastAssistantMessage.content
        .map((c) => {
            if (c.type !== "tool-call") return null;
            if (c.isError) return null;
            if (EMPTY_RESPONSES.includes(c.result)) return null;
            if (wasToolResponseConsumed(c.toolCallId)) return null;
            return {
                id: c.toolCallId as ToolCallId,
                name: c.toolName as ToolName,
                result: c.result as ToolResult,
            };
        })
        .filter(Boolean);

    return results.length > 0 ? results : null;
}

function pickBasic(schema) {
    const picked = omit(schema, ["$schema", "additionalProperties"]);
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
            return {
                name: name as ToolName,
                description: description,
                parameters: pickBasic(parameters),
            };
        })
        .filter(Boolean);
}
