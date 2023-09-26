export class PendingPromise<T> extends Promise<T> {
    resolve: (value: PromiseLike<T> | T) => void;
    reject: (reason?: any) => void;

    constructor(timeoutTime?: number) {
        super((resolve, reject) => {
            this.resolve = resolve;
            this.reject = reject;
        });

        if (timeoutTime) {
            this.#setTimeout(timeoutTime);
        }
    }

    static withTimeout<T>(timeoutTime = 60000) {
        return new PendingPromise<T>(timeoutTime);
    }

    #setTimeout(time: number) {
        const timeout = setTimeout(() => this.reject(new Error(`Timed out waiting (${time}ms) for resolve!`)), time);
        this.finally(() => clearTimeout(timeout));
    }
}
