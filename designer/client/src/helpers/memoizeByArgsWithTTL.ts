import type { MemoizedFunction } from "lodash";
import { memoize } from "lodash";

import { CacheWithTTL } from "./CacheWithTTL";

type Memoized<T extends (...args: any) => any> = T &
    MemoizedFunction & {
        isCached: (...args: Parameters<T>) => boolean;
    };

export const memoizeByArgsWithTTL = <T extends (...args: any) => any>(func: T, ttl?: number, maxSize?: number): Memoized<T> => {
    const memoized = memoize<T>(func, (...args) => args);
    memoized.cache = new CacheWithTTL<Parameters<T>, ReturnType<T>>(ttl, maxSize);
    return Object.assign(memoized, {
        isCached: (...args: Parameters<T>) => memoized.cache.has(args),
    });
};
