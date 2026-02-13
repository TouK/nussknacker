let threadId: string | undefined;
let consumedToolResponses: Set<string> | undefined;

export function getThreadId() {
    return threadId;
}

export function setThreadId(value: string | undefined) {
    if (value === threadId) return;
    threadId = value;
    consumedToolResponses = new Set();
}

export function wasToolResponseConsumed(toolCallId: string): boolean {
    const responses = (consumedToolResponses ||= new Set());
    if (responses.has(toolCallId)) return true;
    responses.add(toolCallId);
    return false;
}

export function resetThreadId() {
    setThreadId(undefined);
}
