import { useSyncExternalStore } from "react";

type ToolCallId = string;
export type Payload = string;
type Result = string | PromiseLike<string>;
type Resolve = (result?: Result) => void;
type HumanAction = { payload: Payload; resolve: Resolve };

const pendingHumanResolvers = new Map<string, HumanAction>();
const listeners = new Set<() => void>();

export function addPendingHumanResolver(toolCallId: ToolCallId, payload: string) {
    return new Promise<string>((resolve) => {
        pendingHumanResolvers.set(toolCallId, { payload, resolve });
        listeners.forEach((l) => l());
    });
}

export function resolveHumanAction(toolCallId: ToolCallId, result?: Result) {
    const action = pendingHumanResolvers.get(toolCallId);
    if (!action) return;
    action.resolve(result);
    pendingHumanResolvers.delete(toolCallId);
    listeners.forEach((l) => l());
}

function subscribe(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
}

export function useToolHumanAction(toolCallId: ToolCallId) {
    return useSyncExternalStore(subscribe, () => {
        const action = pendingHumanResolvers.get(toolCallId);
        return action?.payload;
    });
}
