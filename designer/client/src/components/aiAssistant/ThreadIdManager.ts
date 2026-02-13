export class ThreadIdManager {
    static #threadId: string;
    static #consumedToolResponses: Set<string>;

    static get THREAD_ID() {
        return this.#threadId;
    }

    static set THREAD_ID(value: string | undefined) {
        if (value === this.#threadId) return;
        this.#threadId = value;
        this.#consumedToolResponses = new Set();
    }

    static wasToolResponseConsumed(toolCallId: string): boolean {
        const responses = (this.#consumedToolResponses ||= new Set());
        if (responses.has(toolCallId)) return true;
        responses.add(toolCallId);
        return false;
    }

    static reset() {
        this.THREAD_ID = undefined;
    }
}
