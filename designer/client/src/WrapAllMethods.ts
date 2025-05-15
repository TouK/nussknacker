const NO_WRAP = Symbol("NO_WRAP");

export function NoWrap(target, key: string | symbol) {
    const desc = Object.getOwnPropertyDescriptor(target, key);
    if (desc?.value && typeof desc.value === "function") {
        desc.value[NO_WRAP] = true;
    } else {
        target.constructor.prototype.__noWrapFields ??= new Set<string | symbol>();
        target.constructor.prototype.__noWrapFields.add(key);
    }
}

export function WrapAllMethods(wrapperFn: <F extends (...args: any[]) => any>(fn: F) => (...args: Parameters<F>) => ReturnType<F>) {
    return function <T extends { new (...args: any[]): any }>(OriginalClass: T): T {
        // prototype methods
        Object.getOwnPropertyNames(OriginalClass.prototype).forEach((key) => {
            if (key === "constructor") return;

            const desc = Object.getOwnPropertyDescriptor(OriginalClass.prototype, key);
            const fn = desc?.value;

            if (typeof fn !== "function") return;
            if (fn[NO_WRAP]) return;

            desc.value = wrapperFn(fn);
            Object.defineProperty(OriginalClass.prototype, key, desc);
        });

        // instance field methods
        return class extends OriginalClass {
            constructor(...args: any[]) {
                super(...args);

                const noWrapFields: Set<string | symbol> = (OriginalClass.prototype as any).__noWrapFields ?? new Set();

                Object.getOwnPropertyNames(this).forEach((key) => {
                    if (noWrapFields.has(key)) return;

                    const val = this[key];

                    if (typeof val !== "function") return;

                    this[key] = wrapperFn(val.bind(this));
                });
            }
        };
    };
}
