import {isArray} from "lodash"

export function ensureArray<T>(maybeArray: T | T[] = [], fillSize = 1): T[] {
  return isArray(maybeArray) ? maybeArray : Array(fillSize).fill(maybeArray)
}
