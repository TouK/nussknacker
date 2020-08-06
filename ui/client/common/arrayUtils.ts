import {isArray} from "lodash"

export function ensureArray<T extends any>(maybeArray: T | T[] = []): T[] {
  return isArray(maybeArray) ? maybeArray : [maybeArray]
}
