import {isArray} from "lodash"

export function ensureArray<T extends any>(maybeArray: T | T[] = [], fillSize = 1): T[] {
  return isArray(maybeArray) ? maybeArray : Array(fillSize).fill(maybeArray)
}
