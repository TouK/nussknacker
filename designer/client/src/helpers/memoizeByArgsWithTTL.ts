import { memoize } from "lodash";

import { CacheWithTTL } from "./CacheWithTTL";

export const memoizeByArgsWithTTL = <T extends (...args: any) => any>(func: T, ttl?: number, maxSize?: number) => {
    const memoized = memoize<T>(func, (...args) => args);
    memoized.cache = new CacheWithTTL<Parameters<T>, ReturnType<T>>(ttl, maxSize);
    return memoized;
};
