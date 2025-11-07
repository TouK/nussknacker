const pending = new Map<keyof Window, Promise<any>>();

export function waitForWindowValue<K extends keyof Window>(prop: K): Promise<Window[K]> {
    if (window[prop]) {
        return Promise.resolve(window[prop]);
    }

    if (pending.has(prop)) {
        return pending.get(prop);
    }

    const promise = new Promise<Window[K]>((resolve) => {
        let value;
        Object.defineProperty(window, prop, {
            configurable: true,
            get: () => value,
            set: (newValue) => {
                value = newValue;
                resolve(newValue);
                pending.delete(prop);
                Object.defineProperty(window, prop, { value: newValue, writable: true, configurable: true });
            },
        });
    });

    pending.set(prop, promise);
    return promise;
}
