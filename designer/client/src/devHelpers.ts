export const isProd = process.env.NODE_ENV === "production";
export const isVisualTesting = window["Cypress"];
export const isDev = !isProd && !isVisualTesting;

export function withLogs<A extends unknown[], R>(fn: (...args: A) => R, message?: string): (...args: A) => R {
    return function (...args) {
        const result = fn(...args);
        if (isDev) console.debug(message || fn.name, args, result);
        return result;
    };
}
