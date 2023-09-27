type PromiseResolve<T> = (value: T | PromiseLike<T>) => void;
type PromiseReject<R = any> = (reason?: R) => void;
type PromiseExecutor<T> = (resolve: PromiseResolve<T>, reject: PromiseReject) => void;

export class PendingPromise<T> extends Promise<T> {
    resolve: PromiseResolve<T>;
    reject: PromiseReject;

    // eslint-disable-next-line @typescript-eslint/no-empty-function
    constructor(executor: PromiseExecutor<T> = () => {}) {
        let resolveHandler: PromiseResolve<T>;
        let rejectHandler: PromiseReject;

        super((resolve, reject) => {
            executor(resolve, reject);
            resolveHandler = resolve;
            rejectHandler = reject;
        });

        this.resolve = resolveHandler;
        this.reject = rejectHandler;
    }

    static withTimeout<T>(timeoutTime = 60000) {
        const pendingPromise = new PendingPromise<T>(undefined);
        const timeout = setTimeout(
            () => pendingPromise.reject(new Error(`Timed out waiting (${timeoutTime}ms) for resolve!`)),
            timeoutTime,
        );
        pendingPromise.finally(() => clearTimeout(timeout));
        return pendingPromise;
    }
}

window["PendingPromise"] = PendingPromise;
