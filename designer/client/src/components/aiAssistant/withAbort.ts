export function withAbort<T = unknown>(signal: AbortSignal, callback: () => Promise<T>): Promise<T> {
    if (signal.aborted) return Promise.resolve(null);
    const signalPromise = new Promise<T>((resolve) => {
        signal.addEventListener("abort", () => resolve(null), { once: true });
    });
    return Promise.race([signalPromise, callback()]);
}
