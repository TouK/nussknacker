import type { Tagged } from "type-fest";

import type { ToolCallId } from "./ChatRequest";

export type ThreadId = Tagged<string, "ThreadId">;

let threadId: ThreadId | undefined;
let consumedToolResponses: Set<string> | undefined;

export function getThreadId(): ThreadId {
    return threadId;
}

export function setThreadId(value?: ThreadId) {
    if (value === threadId) return;
    threadId = value;
    consumedToolResponses = new Set();
}

export function wasToolResponseConsumed(toolCallId: ToolCallId): boolean {
    const responses = (consumedToolResponses ||= new Set());
    if (responses.has(toolCallId)) return true;
    responses.add(toolCallId);
    return false;
}

export function resetThreadId() {
    setThreadId(undefined);
}
