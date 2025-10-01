type PromiseResolve<T> = (value: T | PromiseLike<T>) => void;
type PromiseReject<R = any> = (reason?: R) => void;
type PromiseExecutor<T> = (resolve: PromiseResolve<T>, reject: PromiseReject) => void;

export class PendingPromise<T = unknown> extends Promise<T> {
    resolve: PromiseResolve<T>;
    reject: PromiseReject;

    // eslint-disable-next-line @typescript-eslint/no-empty-function
    constructor(executor: PromiseExecutor<T> = () => {}) {
        let resolveHandler: PromiseResolve<T>;
        let rejectHandler: PromiseReject;

        super((resolve, reject) => {
            resolveHandler = resolve;
            rejectHandler = reject;
            executor(resolve, reject);
        });

        this.resolve = resolveHandler;
        this.reject = rejectHandler;
    }

    static withTimeout<T>(timeoutMs = 60000) {
        const pendingPromise = new PendingPromise<T>();
        const timeout = setTimeout(() => pendingPromise.reject(new Error(`Timed out after ${timeoutMs}ms`)), timeoutMs);
        pendingPromise.finally(() => clearTimeout(timeout));
        return pendingPromise;
    }

    static timedResolve(timeoutMs = 60000) {
        const pendingPromise = new PendingPromise<number | false>();
        const timeout = setTimeout(async () => pendingPromise.resolve(timeoutMs), timeoutMs);
        pendingPromise
            .catch(() => false)
            .finally(() => {
                clearTimeout(timeout);
            });
        return pendingPromise;
    }
}
