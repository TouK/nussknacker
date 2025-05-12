import { memoize } from "lodash";

import { TTLMap } from "./TTLMap";

export const ttlMemoize = <T extends (...args: any) => any>(func: T, resolver?: (...args: Parameters<T>) => any) => {
    const memoized = memoize<T>(func, resolver);
    memoized.cache = new TTLMap();
    return memoized;
};
