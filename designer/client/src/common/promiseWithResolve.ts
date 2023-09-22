export interface PromiseWithResolve<T> extends Promise<T> {
    resolve: (value: T | PromiseLike<T>) => void;
}

export function getPromiseWithResolve<T>(): PromiseWithResolve<T> {
    let resolveCallback: (value: T | PromiseLike<T>) => void;

    const promise = new Promise<T>((resolve) => {
        resolveCallback = resolve;
    });

    const handler: ProxyHandler<PromiseWithResolve<T>> = {
        get: (promise, prop, ...args) => {
            // eslint-disable-next-line no-prototype-builtins
            if (prop in promise || promise.hasOwnProperty(prop)) {
                if (typeof promise[prop] === "function") {
                    return promise[prop].bind(promise);
                }
            }

            if (prop === "resolve") {
                return resolveCallback;
            }

            return Reflect.get(promise, prop, ...args);
        },
    };

    return new Proxy(promise as PromiseWithResolve<T>, handler);
}
