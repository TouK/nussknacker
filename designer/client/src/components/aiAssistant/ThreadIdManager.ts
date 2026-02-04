export class ThreadIdManager {
    static #threadId: string;

    static get THREAD_ID() {
        return this.#threadId;
    }

    static set THREAD_ID(value: string | undefined) {
        this.#threadId = value;
    }

    static reset() {
        this.#threadId = undefined;
    }
}
